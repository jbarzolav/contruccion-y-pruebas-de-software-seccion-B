package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.testing.ContenedorDePruebas
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import com.ejemplo.publicarproducto.testing.esperarCondicion
import androidx.lifecycle.ViewModel
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo D-2 - ConsultasViewModel (HU 38 y HU 39).
 *
 * Comprueba que el ViewModel de las consultas ya no habla con Retrofit ni
 * con Gson: delega en `ConversacionesRepositorio`, conserva el orden
 * cronológico y la paginación, y devuelve exactamente los mismos textos
 * que mostraba la pantalla antes de la refactorización.
 */
@RunWith(AndroidJUnit4::class)
class ConsultasViewModelTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas

    /**
     * ViewModels creados por la prueba.
     *
     * Sus colectores de Room viven en `viewModelScope`: hay que cancelar ese
     * alcance ANTES de cerrar la base (lección D-3), o Room consultaría una
     * base ya cerrada y el proceso de pruebas se caería.
     */
    private val viewModels = mutableListOf<ViewModel>()

    /** Crea un ViewModel registrado para cancelar su alcance al terminar. */
    private fun <T : ViewModel> registrar(model: T): T {
        viewModels += model
        return model
    }

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesion = SessionManager(contexto)
        sesion.invalidarSesion()
        servidor = ServidorDePruebas()
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))
    }

    @After
    fun tearDown() {
        // Primero se detienen los colectores de Room y después se cierra la
        // base: al revés, Room consultaría una base ya cerrada.
        viewModels.forEach { it.cancelarAlcance() }
        viewModels.clear()

        ProveedorDatos.fijar(null)
        sesion.invalidarSesion()

        bd.close()
        servidor.cerrar()
    }

    private fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
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
    private val cuerpoMensajes = """
        {"mensajes":[
           {"id":3,"emisorId":5,"emisorRol":"CLIENTE","contenido":"tercero","fecha":"2026-01-01T12:00:00"},
           {"id":2,"emisorId":8,"emisorRol":"VENDEDOR","contenido":"segundo","fecha":"2026-01-01T11:00:00"},
           {"id":1,"emisorId":5,"emisorRol":"CLIENTE","contenido":"primero","fecha":"2026-01-01T10:00:00"}],
         "pagina":0,"tamano":20,"totalElementos":3,"totalPaginas":2,"tieneSiguiente":true}
    """.trimIndent()

    /** Página 1 (los más antiguos), también descendente. */
    private val cuerpoMensajesPagina1 = """
        {"mensajes":[
           {"id":20,"emisorId":5,"emisorRol":"CLIENTE","contenido":"v20","fecha":"2025-12-31T09:00:00"},
           {"id":19,"emisorId":8,"emisorRol":"VENDEDOR","contenido":"v19","fecha":"2025-12-31T08:00:00"}],
         "pagina":1,"tamano":20,"totalElementos":5,"totalPaginas":3,"tieneSiguiente":true}
    """.trimIndent()

    /** Conversación de la página 0 de la conversación 8 (para el cambio). */
    private val cuerpoMensajesOtraConversacion = """
        {"mensajes":[
           {"id":50,"emisorId":5,"emisorRol":"CLIENTE","contenido":"otra","fecha":"2026-02-01T10:00:00"}],
         "pagina":0,"tamano":20,"totalElementos":1,"totalPaginas":1,"tieneSiguiente":false}
    """.trimIndent()

    // ------------------------------------------------------------ estado inicial
    @Test
    fun elEstadoInicialEsElDeLaPantalla() {
        val model = registrar(ConsultasViewModel())
        assertTrue(model.conversaciones.isEmpty())
        assertTrue(model.mensajes.isEmpty())
        assertEquals(0, model.paginaMensajes)
        assertFalse(model.tieneMensajesAnteriores)
        assertFalse(model.cargando)
        assertFalse(model.cargandoMensajes)
        assertFalse(model.enviando)
        assertNull(model.error)
        assertNull(model.errorMensajes)
        assertNull(model.exito)
        assertNull(model.maximo)
        assertEquals("", model.texto)
    }

    // ------------------------------------------------------------ carga
    @Test
    fun elEstadoDeCargaSeActivaAlDescargar() {
        servidor.servidor.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpoConfiguracion)
                .setHeadersDelay(800, TimeUnit.MILLISECONDS)
        )
        val model = registrar(ConsultasViewModel())
        model.cargar(true)

        assertTrue("Debe estar cargando: ${model.cargando}", model.cargando)

        esperar { !model.cargando }
        assertFalse("Debe terminar la carga: ${model.cargando}", model.cargando)
        assertEquals(1000, model.maximo)
    }

    @Test
    fun cargarDescargaLaConfiguracionYElListado() {
        servidor.responder(200, cuerpoConfiguracion)
        servidor.responder(200, cuerpoConversacion)
        val model = registrar(ConsultasViewModel())
        model.cargar(true)
        esperar { model.maximo != null && model.conversaciones.isNotEmpty() }

        assertEquals(1000, model.maximo)
        assertEquals(1, model.conversaciones.size)
        assertEquals(7L, model.conversaciones.first().idConversacion)
        assertEquals("Hola", model.conversaciones.first().ultimoMensaje?.contenido)
        assertNull(model.error)
        assertFalse(model.cargando)

        val rutas = servidor.rutasRecibidas()
        assertTrue("Configuración: $rutas", rutas.contains("/api/conversaciones/configuracion"))
        assertTrue("Listado: $rutas", rutas.contains("/api/conversaciones"))
    }

    @Test
    fun cargarSinListarSoloPideLaConfiguracion() {
        servidor.responder(200, cuerpoConfiguracion)
        val model = registrar(ConsultasViewModel())
        model.cargar(false)
        esperar { model.maximo != null && !model.cargando }

        assertEquals(1000, model.maximo)
        assertTrue(model.conversaciones.isEmpty())
        assertEquals(1, servidor.peticionesRecibidas())
    }

    // ------------------------------------------------------- errores de carga
    @Test
    fun errorDeConfiguracionUsaElTextoPorCodigo() {
        servidor.responder(400, "{}")
        val model = registrar(ConsultasViewModel())
        model.cargar(true)
        esperar { model.error != null }

        assertEquals("Error 400. Intenta nuevamente.", model.error)
        // Sin configuración no se pide el listado (igual que antes).
        assertNull(model.maximo)
        assertTrue(model.conversaciones.isEmpty())
        assertEquals(1, servidor.peticionesRecibidas())
        assertFalse(model.cargando)
    }

    @Test
    fun errorConMessagesUsaElPrimerMensaje() {
        servidor.responderError(400, "No tienes permiso para consultar")
        val model = registrar(ConsultasViewModel())
        model.cargar(true)
        esperar { model.error != null }

        assertEquals("No tienes permiso para consultar", model.error)
    }

    @Test
    fun sinConexionMuestraElMensajeCrudoSinPrefijo() {
        servidor.apagar()
        val model = registrar(ConsultasViewModel())
        model.cargar(true)
        esperar { model.error != null }

        val error = model.error!!
        assertTrue("Mensaje crudo: $error", error.isNotBlank())
        assertFalse(
            "No debe agregar prefijo de conexión: $error",
            error.startsWith("No hay conexión")
        )
        assertFalse("No debe usar el texto por código: $error", error.startsWith("Error "))
        assertFalse(model.cargando)
    }

    // ------------------------------------------------------- mensajes
    @Test
    fun cargarMensajesInvierteElOrdenDelBackend() {
        servidor.responder(200, cuerpoMensajes)
        val model = registrar(ConsultasViewModel())
        model.cargarMensajes(7)
        esperar { model.mensajes.isNotEmpty() }

        assertEquals(listOf(1L, 2L, 3L), model.mensajes.map { it.id })
        assertEquals(listOf("primero", "segundo", "tercero"), model.mensajes.map { it.contenido })
        assertEquals(0, model.paginaMensajes)
        assertTrue(model.tieneMensajesAnteriores)
        assertNull(model.errorMensajes)
        assertFalse(model.cargandoMensajes)
    }

    @Test
    fun cargarUnaPaginaAnteriorSeAntepone() {
        servidor.responder(200, cuerpoMensajes)
        servidor.responder(200, cuerpoMensajesPagina1)
        val model = registrar(ConsultasViewModel())
        model.cargarMensajes(7, 0)
        esperar { model.mensajes.size == 3 }

        model.cargarMensajes(7, 1)
        esperar { model.paginaMensajes == 1 && model.mensajes.size == 5 }

        // Lo más antiguo primero: la lista sigue en orden cronológico.
        assertEquals(listOf(19L, 20L, 1L, 2L, 3L), model.mensajes.map { it.id })
        assertEquals(1, model.paginaMensajes)
        assertTrue(model.tieneMensajesAnteriores)
        assertFalse(model.cargandoMensajes)
        assertNull(model.errorMensajes)
    }

    @Test
    fun cambiarDeConversacionReemplazaLosMensajes() {
        servidor.responder(200, cuerpoMensajes)
        servidor.responder(200, cuerpoMensajesPagina1)
        servidor.responder(200, cuerpoMensajesOtraConversacion)
        val model = registrar(ConsultasViewModel())
        model.cargarMensajes(7, 0)
        esperar { model.mensajes.size == 3 }
        model.cargarMensajes(7, 1)
        esperar { model.mensajes.size == 5 }

        model.cargarMensajes(8, 0)
        esperar { model.mensajes.size == 1 }

        assertEquals(listOf(50L), model.mensajes.map { it.id })
        assertEquals(0, model.paginaMensajes)
        assertFalse(model.tieneMensajesAnteriores)
    }

    @Test
    fun errorDeMensajesUsaElTextoPorCodigo() {
        servidor.responder(404, "{}")
        val model = registrar(ConsultasViewModel())
        model.cargarMensajes(7)
        esperar { model.errorMensajes != null }

        assertEquals("Error 404. Intenta nuevamente.", model.errorMensajes)
        assertTrue(model.mensajes.isEmpty())
        assertFalse(model.cargandoMensajes)
    }

    // ------------------------------------------------------------ envío
    @Test
    fun enviarConsultaUsaElPostDeConversaciones() {
        servidor.responder(200, cuerpoConfiguracion)
        servidor.responder(
            200,
            """{"idConversacion":7,"productoId":3,"productoNombre":"Teclado",
                "productoImagenUrl":null,"fechaCreacion":"2026-01-01T10:00:00","mensajes":[]}"""
        )
        val model = registrar(ConsultasViewModel())
        model.cargar(false)
        esperar { model.maximo != null }

        model.texto = "¿Tiene stock?"
        model.enviar(productoId = 3, conversacionId = null)
        esperar { model.exito != null }

        assertEquals("Mensaje enviado correctamente.", model.exito)
        assertNull(model.error)
        assertEquals("", model.texto)
        assertFalse(model.enviando)

        val peticiones = servidor.peticiones()
        val consulta = peticiones.first { it.path == "/api/conversaciones" }
        assertEquals("POST", consulta.method)
        val cuerpo = consulta.body.readUtf8()
        assertTrue("Producto: $cuerpo", cuerpo.contains("\"productoId\":3"))
        assertTrue("Mensaje: $cuerpo", cuerpo.contains("¿Tiene stock?"))
    }

    @Test
    fun enviarRespuestaUsaElPostDeMensajesYRecarga() {
        // Despachador por RUTA (no FIFO): la configuración y el historial se
        // piden en paralelo y el orden de llegada no está garantizado.
        servidor.servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val json = when {
                    request.method == "POST" &&
                        request.path == "/api/conversaciones/7/mensajes" ->
                        """{"id":12,"emisorId":8,"emisorRol":"VENDEDOR","contenido":"Sí, quedan 2","fecha":"2026-01-01T12:00:00"}"""

                    request.path == "/api/conversaciones/configuracion" ->
                        cuerpoConfiguracion

                    request.method == "GET" &&
                        request.path?.startsWith("/api/conversaciones/7/mensajes") == true ->
                        cuerpoMensajes

                    request.method == "GET" && request.path == "/api/conversaciones" ->
                        cuerpoConversacion

                    else -> """{"status":404,"error":"Not Found","messages":["Ruta inesperada"]}"""
                }
                val codigo = if (json.contains("\"status\":404")) 404 else 200
                return MockResponse()
                    .setResponseCode(codigo)
                    .setHeader("Content-Type", "application/json")
                    .setBody(json)
            }
        }
        val model = registrar(ConsultasViewModel())
        // Sin configuración `maximo` es null y enviar() no hace nada: hay que
        // cargarla primero (igual que en la pantalla real).
        model.cargar(false)
        esperar { model.maximo != null }

        model.texto = "¿Tiene stock?"
        model.enviar(productoId = null, conversacionId = 7)
        esperar { model.exito != null && !model.enviando }

        assertEquals("Mensaje enviado correctamente.", model.exito)
        assertNull(model.error)
        assertEquals("", model.texto)
        assertFalse(model.enviando)

        // Esperar a que la recarga completa termine (configuración, historial
        // y listado) antes de leer la cola: rutasRecibidas() es no bloqueante
        // y devolvería solo las que hayan llegado hasta ese instante.
        esperar { model.conversaciones.isNotEmpty() && model.mensajes.isNotEmpty() }
        val rutas = servidor.rutasRecibidas()
        assertTrue("Debe responder: $rutas", rutas.contains("/api/conversaciones/7/mensajes"))
        assertTrue("Debe revalidar: $rutas", rutas.contains("/api/conversaciones/configuracion"))
        assertTrue("Debe refrescar: $rutas", rutas.contains("/api/conversaciones"))
        // Tras recargar, el historial queda en orden cronológico.
        assertEquals(listOf(1L, 2L, 3L), model.mensajes.map { it.id })
    }

    @Test
    fun unaLongitudInvalidaNoLlamaAlBackend() {
        servidor.responder(200, cuerpoConfiguracion)
        val model = registrar(ConsultasViewModel())
        model.cargar(false)
        esperar { model.maximo != null }

        val peticionesAntes = servidor.peticionesRecibidas()
        model.texto = "x".repeat(1001)
        model.enviar(productoId = 3, conversacionId = null)

        assertEquals("Escribe entre 1 y 1000 caracteres.", model.error)
        assertNull(model.exito)
        assertFalse(model.enviando)
        assertEquals(
            "No debe llamar al backend",
            peticionesAntes,
            servidor.peticionesRecibidas()
        )
    }

    @Test
    fun errorDeEnvioUsaElTextoPorCodigo() {
        servidor.responder(200, cuerpoConfiguracion)
        servidor.responder(400, "{}")
        val model = registrar(ConsultasViewModel())
        model.cargar(false)
        esperar { model.maximo != null }

        model.texto = "¿Tiene stock?"
        model.enviar(productoId = 3, conversacionId = null)
        esperar { model.error != null }

        assertEquals("Error 400. Intenta nuevamente.", model.error)
        assertNull(model.exito)
        assertFalse(model.enviando)
        // El texto solo se limpia en el éxito: ante un error se conserva para
        // que el usuario pueda reintentar (comportamiento previo preservado).
        assertEquals("¿Tiene stock?", model.texto)
    }

    @Test
    fun elRefrescoSilenciosoNoReemplazaLosErroresVisibles() {
        servidor.responder(200, cuerpoConversacion)
        val model = registrar(ConsultasViewModel())
        model.actualizarSilencioso(null)
        esperar { model.conversaciones.isNotEmpty() }

        assertEquals(1, model.conversaciones.size)
        assertNull(model.error)
    }
}
