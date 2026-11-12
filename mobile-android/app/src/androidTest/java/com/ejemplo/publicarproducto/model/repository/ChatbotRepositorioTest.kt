package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo D-1 - ChatbotRepositorio en el dispositivo (HU 16 y HU 17).
 *
 * Comprueba en el runtime real del emulador que el repositorio usa la
 * interfaz `ProductoApi` existente, conserva los contratos HTTP de los dos
 * endpoints y devuelve exactamente los mismos textos que mostraba la
 * pantalla antes de la refactorización.
 */
class ChatbotRepositorioTest {

    private lateinit var servidor: ServidorDePruebas
    private lateinit var repositorio: ChatbotRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebas()
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
    fun recomendarProductosUsaElEndpointDeRecomendaciones() = runBlocking {
        servidor.responder(200, cuerpoExito)

        val resultado = repositorio.recomendarProductos("teclado mecánico")

        assertTrue("Debería recomendar: $resultado", resultado is ResultadoApi.Exito)
        val respuesta = (resultado as ResultadoApi.Exito).valor
        assertEquals("Te recomiendo estos productos", respuesta.mensaje)
        assertEquals("Teclado", respuesta.productos?.first()?.nombre)

        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/chatbot/recomendaciones", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue(
            "Debe enviar la consulta: $cuerpo",
            cuerpo.contains("\"consulta\":\"teclado mecánico\"")
        )
    }

    // ------------------------------------------------------------------ HU 17
    @Test
    fun consultarComponenteTecnicoUsaSuEndpoint() = runBlocking {
        servidor.responder(200, cuerpoExito)

        val resultado = repositorio.consultarComponenteTecnico("¿Para qué sirve una protoboard?")

        assertTrue("Debería responder: $resultado", resultado is ResultadoApi.Exito)

        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/chatbot/consultas-tecnicas", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue(
            "Debe enviar la consulta: $cuerpo",
            cuerpo.contains("protoboard")
        )
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
        servidor.responder(500, "{}")

        val resultado = repositorio.consultarComponenteTecnico("zzz")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 500: no se pudo consultar.",
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
        assertTrue(
            "Mensaje de red: ${error.mensaje}",
            error.mensaje.startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }
}
