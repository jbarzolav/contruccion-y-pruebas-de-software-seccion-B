package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo D-2 - ConversacionesRepositorio en el dispositivo (HU 38 y HU 39).
 *
 * Comprueba en el runtime real del emulador que el repositorio usa la
 * interfaz `ConversacionApi` existente, conserva los contratos HTTP de los
 * cinco endpoints, invierte el orden descendente del backend UNA sola vez
 * y devuelve exactamente los mismos textos que mostraba la pantalla.
 */
class ConversacionesRepositorioTest {

    private lateinit var servidor: ServidorDePruebas
    private lateinit var repositorio: ConversacionesRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebas()
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

    @Test
    fun configuracionUsaSuRuta() = runBlocking {
        servidor.responder(200, cuerpoConfiguracion)

        val resultado = repositorio.configuracion()

        assertTrue("Debería configurar: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(1000, (resultado as ResultadoApi.Exito).valor.longitudMaxima)
        assertEquals("/api/conversaciones/configuracion", servidor.ultimaRuta())
    }

    @Test
    fun listarUsaLaRutaDeConversaciones() = runBlocking {
        servidor.responder(200, cuerpoConversacion)

        val resultado = repositorio.listarConversaciones()

        assertTrue("Debería listar: $resultado", resultado is ResultadoApi.Exito)
        val conversaciones = (resultado as ResultadoApi.Exito).valor
        assertEquals(1, conversaciones.size)
        assertEquals(7L, conversaciones.first().idConversacion)
        assertEquals("Hola", conversaciones.first().ultimoMensaje?.contenido)
        assertEquals("/api/conversaciones", servidor.ultimaRuta())
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
        // takeRequest() consume la cola: se lee UNA sola vez.
        val ruta = servidor.ultimaRuta()
        assertTrue(
            "Debe usar el endpoint de mensajes: $ruta",
            ruta.startsWith("/api/conversaciones/7/mensajes")
        )
    }

    @Test
    fun laPaginaSolicitaElIndiceCorrecto() = runBlocking {
        servidor.responder(200, cuerpoMensajesDescendente)

        val resultado = repositorio.obtenerMensajes(idConversacion = 7, pagina = 2)

        assertTrue(resultado is ResultadoApi.Exito)
        // Se consume la cola UNA sola vez para no bloquear takeRequest().
        val ruta = servidor.ultimaRuta()
        assertTrue("Debe pedir la página 2: $ruta", ruta.contains("pagina=2"))
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
        assertTrue("Producto: $cuerpo", cuerpo.contains("\"productoId\":3"))
        assertTrue("Mensaje: $cuerpo", cuerpo.contains("¿Tiene stock?"))
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
        assertTrue("Respuesta: $cuerpo", cuerpo.contains("\"mensaje\":\"Sí, quedan 2\""))
    }

    @Test
    fun errorHttpSinMessagesUsaElTextoPorCodigo() = runBlocking {
        servidor.responder(400, "{}")

        val resultado = repositorio.listarConversaciones()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 400. Intenta nuevamente.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun errorConMessagesUsaElPrimerMensaje() = runBlocking {
        servidor.responderError(400, "La conversación ya no existe")

        val resultado = repositorio.responderConsulta(idConversacion = 7, mensaje = "hola")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "La conversación ya no existe",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun sinConexionConservaElMensajeCrudoSinPrefijo() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.configuracion()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
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
}
