package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.ChatbotResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebasUnitario
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo D-1 - ChatbotRepositorio (HU 16 y HU 17).
 *
 * Pruebas unitarias (JVM, sin emulador) contra un servidor HTTP real de
 * pruebas: verifican los contratos de los dos endpoints, los textos de
 * error exactos que mostraba la interfaz y que una cancelación de
 * coroutine se propaga en lugar de convertirse en un error de red.
 */
class ChatbotRepositorioTest {

    private lateinit var servidor: ServidorDePruebasUnitario
    private lateinit var repositorio: ChatbotRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebasUnitario()
        repositorio = ChatbotRepositorio(servidor.api)
    }

    @After
    fun tearDown() {
        servidor.cerrar()
    }

    private val cuerpoExito = """
        {"mensaje":"Te recomiendo estos productos",
         "productos":[{"id":7,"nombre":"Teclado","precio":150.00,"stock":5,
                       "categoria":"Periféricos","imagenUrl":"/img/teclado.png"}]}
    """.trimIndent()

    // ------------------------------------------------------------------ HU 16
    @Test
    fun recomendarProductosDevuelveElMensajeYLosProductos() = runBlocking {
        servidor.responder(200, cuerpoExito)

        val resultado = repositorio.recomendarProductos("teclado mecánico")

        assertTrue("Debería recomendar: $resultado", resultado is ResultadoApi.Exito)
        val respuesta = (resultado as ResultadoApi.Exito).valor
        assertEquals("Te recomiendo estos productos", respuesta.mensaje)
        assertEquals(1, respuesta.productos?.size)
        assertEquals("Teclado", respuesta.productos?.first()?.nombre)

        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/chatbot/recomendaciones", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar la consulta: $cuerpo", cuerpo.contains("\"consulta\":\"teclado mecánico\""))
    }

    @Test
    fun consultarComponenteTecnicoUsaSuEndpoint() = runBlocking {
        servidor.responder(200, cuerpoExito)

        val resultado = repositorio.consultarComponenteTecnico("¿Para qué sirve una protoboard?")

        assertTrue("Debería responder: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(
            "Te recomiendo estos productos",
            (resultado as ResultadoApi.Exito).valor.mensaje
        )

        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/chatbot/consultas-tecnicas", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar la consulta: $cuerpo", cuerpo.contains("protoboard"))
    }

    @Test
    fun unaRespuestaSinMensajeQuedaComoExito() = runBlocking {
        servidor.responder(200, """{"mensaje":null,"productos":null}""")

        val resultado = repositorio.recomendarProductos("notebook")

        assertTrue("Sin mensaje no es error: $resultado", resultado is ResultadoApi.Exito)
        val respuesta = (resultado as ResultadoApi.Exito).valor
        // El ViewModel es quien pinta "No hay una respuesta disponible."
        assertNull(respuesta.mensaje)
        assertNull(respuesta.productos)
    }

    // ------------------------------------------------------- errores HTTP
    @Test
    fun unErrorConMessagesUsaElPrimerMensaje() = runBlocking {
        servidor.responderError(400, "No pude encontrar productos para esa consulta")

        val resultado = repositorio.recomendarProductos("zzz")

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(400, error.codigo)
        assertEquals(
            "No pude encontrar productos para esa consulta",
            error.mensaje
        )
    }

    @Test
    fun unErrorSinMessagesUsaElTextoPorCodigo() = runBlocking {
        servidor.responder(400, "{}")

        val resultado = repositorio.consultarComponenteTecnico("zzz")

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(400, error.codigo)
        assertEquals("Error 400: no se pudo consultar.", error.mensaje)
    }

    @Test
    fun unaRespuestaSinCuerpoConservaElTextoPorCodigo() = runBlocking {
        // 204: Retrofit no invoca el convertidor, el cuerpo llega nulo.
        servidor.responder(204, "")

        val resultado = repositorio.recomendarProductos("notebook")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 204: no se pudo consultar.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun sinConexionConservaElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.recomendarProductos("notebook")

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue(error.temporal)
        assertTrue(
            "Mensaje de red: ${error.mensaje}",
            error.mensaje.startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun unaExcepcionInesperadaSeConvierteEnErrorDeRed() = runBlocking {
        // 200 con JSON inválido: Gson lanza una excepción que no es IOException.
        servidor.responder(200, """{"mensaje":,"productos":}""")

        val resultado = repositorio.recomendarProductos("notebook")

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue(
            "Excepción inesperada: ${error.mensaje}",
            error.mensaje.startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    // ------------------------------------------------------- cancelación
    @Test
    fun laCancelacionSePropagaYNoSeConvierteEnResultadoDeError() = runBlocking {
        // Petición que nunca responde: la corrutina queda suspendida en la red.
        servidor.servidor.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        var resultado: ResultadoApi<ChatbotResponse>? = null
        var fallo: Throwable? = null

        val trabajo = launch {
            try {
                resultado = repositorio.recomendarProductos("notebook")
            } catch (e: Throwable) {
                fallo = e
            }
        }

        val limite = System.currentTimeMillis() + 3_000L
        while (servidor.peticionesRecibidas() == 0 && System.currentTimeMillis() < limite) {
            delay(50)
        }
        delay(200)

        trabajo.cancel()
        trabajo.join()

        assertNull(
            "La cancelación no debe convertirse en ResultadoApi: $resultado",
            resultado
        )
        assertTrue(
            "Debe propagarse como CancellationException: $fallo",
            fallo is CancellationException
        )
    }
}
