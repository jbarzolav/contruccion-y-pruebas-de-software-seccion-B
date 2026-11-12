package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.MensajeConsulta
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo D-2 - ConversacionesRepositorio (HU 38 y HU 39).
 *
 * Pruebas unitarias (JVM, sin emulador) contra un servidor HTTP real de
 * pruebas: verifican los contratos de los cinco endpoints, el ORDEN
 * CRONOLÓGICO de los mensajes (el backend los envía descendentes), la
 * paginación y la acumulación de páginas, además de los textos de error
 * exactos que mostraba la interfaz.
 */
class ConversacionesRepositorioTest {

    private lateinit var servidor: ServidorDePruebasUnitario
    private lateinit var repositorio: ConversacionesRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebasUnitario()
        repositorio = ConversacionesRepositorio(servidor.conversacionApi)
    }

    @After
    fun tearDown() {
        servidor.cerrar()
    }

    private val cuerpoConfiguracion = """{"longitudMaxima":1000}"""

    private val cuerpoConversacion = """
        [{"idConversacion":7,"productoId":3,"productoNombre":"Teclado",
          "productoImagenUrl":"/img/t.png","productoDisponible":true,
          "fechaCreacion":"2026-01-01T10:00:00",
          "ultimoMensaje":{"id":11,"emisorId":5,"emisorRol":"CLIENTE",
                           "contenido":"Hola","fecha":"2026-01-01T10:00:00"}}]
    """.trimIndent()

    /** Backend en orden DESCENDENTE: 3 (nuevo) primero, 1 (viejo) al final. */
    private val cuerpoMensajesDescendente = """
        {"mensajes":[
           {"id":3,"emisorId":5,"emisorRol":"CLIENTE","contenido":"tercero","fecha":"2026-01-01T12:00:00"},
           {"id":2,"emisorId":8,"emisorRol":"VENDEDOR","contenido":"segundo","fecha":"2026-01-01T11:00:00"},
           {"id":1,"emisorId":5,"emisorRol":"CLIENTE","contenido":"primero","fecha":"2026-01-01T10:00:00"}],
         "pagina":0,"tamano":20,"totalElementos":3,"totalPaginas":1,"tieneSiguiente":false}
    """.trimIndent()

    /** Página 1 (mensajes más antiguos), también descendente. */
    private val cuerpoMensajesPagina1 = """
        {"mensajes":[
           {"id":20,"emisorId":5,"emisorRol":"CLIENTE","contenido":"v20","fecha":"2025-12-31T09:00:00"},
           {"id":19,"emisorId":8,"emisorRol":"VENDEDOR","contenido":"v19","fecha":"2025-12-31T08:00:00"}],
         "pagina":1,"tamano":20,"totalElementos":5,"totalPaginas":3,"tieneSiguiente":true}
    """.trimIndent()

    private fun mensaje(id: Long, contenido: String): MensajeConsulta =
        MensajeConsulta(
            id = id,
            emisorId = 5,
            emisorRol = "CLIENTE",
            contenido = contenido,
            fecha = "2026-01-01T10:00:00"
        )

    // ------------------------------------------------------------------ HU 38
    @Test
    fun configuracionUsaSuRuta() = runBlocking {
        servidor.responder(200, cuerpoConfiguracion)

        val resultado = repositorio.configuracion()

        assertTrue("Debería configurar: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(1000, (resultado as ResultadoApi.Exito).valor.longitudMaxima)
        // takeRequest() consume la cola: se lee UNA sola vez y se comprueban
        // ruta y método sobre la misma petición.
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/conversaciones/configuracion", peticion.path)
        assertEquals("GET", peticion.method)
    }

    @Test
    fun listarUsaLaRutaDeConversaciones() = runBlocking {
        servidor.responder(200, cuerpoConversacion)

        val resultado = repositorio.listarConversaciones()

        assertTrue("Debería listar: $resultado", resultado is ResultadoApi.Exito)
        val conversaciones = (resultado as ResultadoApi.Exito).valor
        assertEquals(1, conversaciones.size)
        assertEquals(7L, conversaciones.first().idConversacion)
        assertEquals("/api/conversaciones", servidor.ultimaRuta())
    }

    @Test
    fun listarConservaElUltimoMensaje() = runBlocking {
        servidor.responder(200, cuerpoConversacion)

        val resultado = repositorio.listarConversaciones()

        val ultimo = (resultado as ResultadoApi.Exito).valor.first().ultimoMensaje
        assertEquals("Hola", ultimo?.contenido)
        assertEquals(11L, ultimo?.id)
    }

    @Test
    fun losMensajesSalenEnOrdenCronologico() = runBlocking {
        servidor.responder(200, cuerpoMensajesDescendente)

        val resultado = repositorio.obtenerMensajes(idConversacion = 7)

        assertTrue("Debería leer: $resultado", resultado is ResultadoApi.Exito)
        val pagina = (resultado as ResultadoApi.Exito).valor
        // El backend envía 3, 2, 1: aquí debe llegar 1, 2, 3 (cronológico).
        assertEquals(listOf(1L, 2L, 3L), pagina.mensajes.map { it.id })
        assertEquals(listOf("primero", "segundo", "tercero"), pagina.mensajes.map { it.contenido })
        assertEquals("/api/conversaciones/7/mensajes?pagina=0&tamano=20", servidor.ultimaRuta())
    }

    @Test
    fun laPaginaSolicitaElIndiceYConservaElCursor() = runBlocking {
        servidor.responder(200, cuerpoMensajesPagina1)

        val resultado = repositorio.obtenerMensajes(idConversacion = 7, pagina = 1)

        assertTrue(resultado is ResultadoApi.Exito)
        val pagina = (resultado as ResultadoApi.Exito).valor
        assertEquals(listOf(19L, 20L), pagina.mensajes.map { it.id })
        assertEquals(1, pagina.pagina)
        assertTrue(pagina.tieneSiguiente)
        // Se consume la cola UNA sola vez para no bloquear takeRequest().
        val ruta = servidor.ultimaRuta()
        assertTrue("Debe pedir la página 1: $ruta", ruta.contains("pagina=1"))
    }

    @Test
    fun acumularLaPaginaCeroReemplazaLosMensajes() {
        val pagina0 = listOf(mensaje(1, "primero"), mensaje(2, "segundo"))

        val acumulados = repositorio.acumularMensajes(
            pagina = 0,
            mensajesPagina = pagina0,
            actuales = listOf(mensaje(99, "anterior"))
        )

        assertEquals(listOf(1L, 2L), acumulados.map { it.id })
    }

    @Test
    fun acumularUnaPaginaAnteriorSeAntepone() {
        val actuales = listOf(mensaje(19, "v19"), mensaje(20, "v20"))
        val pagina1 = listOf(mensaje(1, "primero"), mensaje(2, "segundo"))

        val acumulados = repositorio.acumularMensajes(
            pagina = 1,
            mensajesPagina = pagina1,
            actuales = actuales
        )

        // Los más antiguos primero: la lista sigue en orden cronológico.
        assertEquals(listOf(1L, 2L, 19L, 20L), acumulados.map { it.id })
    }

    @Test
    fun acumularNoDuplicaIdentificadores() {
        val actuales = listOf(mensaje(1, "primero"), mensaje(2, "segundo"))
        val paginaAnterior = listOf(mensaje(3, "tercero"))

        val acumulados = repositorio.acumularMensajes(
            pagina = 2,
            mensajesPagina = paginaAnterior,
            actuales = actuales
        )

        val ids = acumulados.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(listOf(3L, 1L, 2L), ids)
    }

    @Test
    fun consultarUsaElPostDeConversaciones() = runBlocking {
        servidor.responder(
            200,
            """{"idConversacion":7,"productoId":3,"productoNombre":"Teclado",
                "productoImagenUrl":null,"fechaCreacion":"2026-01-01T10:00:00","mensajes":[]}"""
        )

        val resultado = repositorio.consultarSobreProducto(productoId = 3, mensaje = "¿Tiene stock?")

        assertTrue("Debería consultar: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(7L, (resultado as ResultadoApi.Exito).valor.idConversacion)

        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/conversaciones", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar el producto: $cuerpo", cuerpo.contains("\"productoId\":3"))
        assertTrue("Debe enviar el mensaje: $cuerpo", cuerpo.contains("\"mensaje\":\"¿Tiene stock?\""))
    }

    @Test
    fun responderUsaElPostDeMensajes() = runBlocking {
        servidor.responder(
            200,
            """{"id":12,"emisorId":8,"emisorRol":"VENDEDOR","contenido":"Sí, quedan 2","fecha":"2026-01-01T12:00:00"}"""
        )

        val resultado = repositorio.responderConsulta(idConversacion = 7, mensaje = "Sí, quedan 2")

        assertTrue("Debería responder: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(12L, (resultado as ResultadoApi.Exito).valor.id)

        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/conversaciones/7/mensajes", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar la respuesta: $cuerpo", cuerpo.contains("\"mensaje\":\"Sí, quedan 2\""))
    }

    // ------------------------------------------------------- errores HTTP
    @Test
    fun errorHttpSinMessagesUsaElTextoPorCodigo() = runBlocking {
        servidor.responder(400, "{}")

        val resultado = repositorio.listarConversaciones()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(400, error.codigo)
        assertEquals("Error 400. Intenta nuevamente.", error.mensaje)
    }

    @Test
    fun errorConMessagesUsaElPrimerMensaje() = runBlocking {
        servidor.responderError(400, "La conversación ya no existe")

        val resultado = repositorio.responderConsulta(idConversacion = 7, mensaje = "hola")

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(400, error.codigo)
        assertEquals("La conversación ya no existe", error.mensaje)
    }

    @Test
    fun sinConexionConservaElMensajeCrudoSinPrefijo() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.configuracion()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue(error.temporal)
        assertTrue("Mensaje crudo: ${error.mensaje}", error.mensaje.isNotBlank())
        assertFalse(
            "No debe agregar prefijo de conexión: ${error.mensaje}",
            error.mensaje.startsWith("No hay conexión")
        )
        assertFalse(
            "No debe usar el texto por código: ${error.mensaje}",
            error.mensaje.startsWith("Error ")
        )
    }

    @Test
    fun unaExcepcionInesperadaSeConvierteEnError() = runBlocking {
        // 200 con JSON inválido: Gson lanza una excepción que no es IOException.
        servidor.responder(200, """{"longitudMaxima":,"mensajes":}""")

        val resultado = repositorio.obtenerMensajes(idConversacion = 7)

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue("Excepción inesperada: ${error.mensaje}", error.mensaje.isNotBlank())
        assertFalse(
            "No debe agregar prefijo: ${error.mensaje}",
            error.mensaje.startsWith("No hay conexión")
        )
    }

    // ------------------------------------------------------- cancelación
    @Test
    fun laCancelacionSePropagaYNoSeConvierteEnResultadoDeError() = runBlocking {
        servidor.servidor.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        var resultado: ResultadoApi<*>? = null
        var fallo: Throwable? = null

        val trabajo = launch {
            try {
                resultado = repositorio.listarConversaciones()
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
