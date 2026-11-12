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
import okhttp3.mockwebserver.MockResponse
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
 * Grupo D-1 - ChatbotViewModel (HU 16 y HU 17).
 *
 * Comprueba que el ViewModel del chatbot ya no habla con Retrofit ni con
 * Gson: delega en `ChatbotRepositorio`, conserva los mensajes exactos que
 * mostraba la interfaz y termina siempre el estado de carga.
 */
@RunWith(AndroidJUnit4::class)
class ChatbotViewModelTest {

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

    private val cuerpoExito = """
        {"mensaje":"Te recomiendo estos productos",
         "productos":[{"id":7,"nombre":"Teclado","precio":150.00,"stock":5,
                       "categoria":"Periféricos","imagenUrl":"/img/teclado.png"}]}
    """.trimIndent()

    // ------------------------------------------------------------ estado inicial
    @Test
    fun elEstadoInicialEsElDeLaPantalla() {
        val model = registrar(ChatbotViewModel())
        assertEquals(ModoChatbot.RECOMENDACIONES, model.modo)
        assertEquals(1, model.mensajes.size)
        assertTrue("Es un saludo del asistente", !model.mensajes.first().esUsuario)
        assertTrue(
            "Mensaje inicial: ${model.mensajes.first().texto}",
            model.mensajes.first().texto.startsWith("¡Hola! 👋")
        )
        assertFalse(model.enviando)
        assertNull(model.mensajeError)
    }

    @Test
    fun unaConsultaVaciaNoLlamaAlBackend() {
        servidor.responder(200, cuerpoExito)
        val model = registrar(ChatbotViewModel())
        model.enviarConsulta("   ")

        assertEquals(1, model.mensajes.size)
        assertEquals(0, servidor.peticionesRecibidas())
        assertFalse(model.enviando)
    }

    // ------------------------------------------------------------- carga
    @Test
    fun duranteLaCargaSeActivaElEstadoDeEnvio() {
        servidor.servidor.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpoExito)
                .setHeadersDelay(800, TimeUnit.MILLISECONDS)
        )
        val model = registrar(ChatbotViewModel())
        model.enviarConsulta("teclado")

        assertTrue("Debe estar enviando: ${model.enviando}", model.enviando)
        // Saludo + consulta del usuario: la respuesta aún no llegó.
        assertEquals("La respuesta aún no llegó", 2, model.mensajes.size)

        esperar { !model.enviando }
        assertFalse("Debe terminar la carga: ${model.enviando}", model.enviando)
        assertEquals(3, model.mensajes.size)
    }

    // ------------------------------------------------------------ éxito
    @Test
    fun unaRecomendacionAgregaElMensajeYLasTarjetas() {
        servidor.responder(200, cuerpoExito)
        val model = registrar(ChatbotViewModel())
        model.enviarConsulta("teclado mecánico")
        esperar { model.mensajes.size == 3 }

        assertEquals(3, model.mensajes.size)
        val pregunta = model.mensajes[1]
        assertTrue("La consulta va del usuario", pregunta.esUsuario)
        assertEquals("teclado mecánico", pregunta.texto)

        val respuesta = model.mensajes[2]
        assertFalse("La respuesta es del asistente", respuesta.esUsuario)
        assertEquals("Te recomiendo estos productos", respuesta.texto)
        assertEquals(1, respuesta.productos.size)
        assertEquals("Teclado", respuesta.productos.first().nombre)

        assertFalse(model.enviando)
        assertNull(model.mensajeError)

        val rutas = servidor.rutasRecibidas()
        assertTrue("Debe usar el endpoint HU 16: $rutas", rutas.contains("/api/chatbot/recomendaciones"))
    }

    @Test
    fun lasConsultasTecnicasNoPintanTarjetas() {
        servidor.responder(200, cuerpoExito)
        val model = registrar(ChatbotViewModel())
        model.cambiarModo(ModoChatbot.CONSULTAS_TECNICAS)

        model.enviarConsulta("¿Para qué sirve una protoboard?")
        esperar { model.mensajes.size == 3 }

        assertEquals(ModoChatbot.CONSULTAS_TECNICAS, model.modo)
        assertEquals("Te recomiendo estos productos", model.mensajes[2].texto)
        assertTrue(
            "HU 17 no pinta productos",
            model.mensajes[2].productos.isEmpty()
        )

        val rutas = servidor.rutasRecibidas()
        assertTrue("Debe usar el endpoint HU 17: $rutas", rutas.contains("/api/chatbot/consultas-tecnicas"))
    }

    // ------------------------------------------------------- error del repositorio
    @Test
    fun unErrorConMessagesMuestraElPrimerMensajeDelBackend() {
        servidor.responderError(400, "No pude encontrar productos para esa consulta")
        val model = registrar(ChatbotViewModel())
        model.enviarConsulta("zzz")
        esperar { model.mensajeError != null }

        assertEquals(
            "No pude encontrar productos para esa consulta",
            model.mensajeError
        )
        assertEquals(
            "⚠️ No pude encontrar productos para esa consulta",
            model.mensajes.last().texto
        )
        assertFalse(model.enviando)
    }

    @Test
    fun unErrorSinMessagesMuestraElTextoPorCodigo() {
        servidor.responder(400, "{}")
        val model = registrar(ChatbotViewModel())
        model.enviarConsulta("zzz")
        esperar { model.mensajeError != null }

        assertEquals("Error 400: no se pudo consultar.", model.mensajeError)
        assertEquals(
            "⚠️ Error 400: no se pudo consultar.",
            model.mensajes.last().texto
        )
        assertFalse(model.enviando)
    }

    @Test
    fun sinConexionMuestraElMensajeDeRed() {
        servidor.apagar()
        val model = registrar(ChatbotViewModel())
        model.enviarConsulta("notebook")
        esperar { model.mensajeError != null }

        assertTrue(
            "Mensaje de red: ${model.mensajeError}",
            model.mensajeError!!.startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
        assertTrue(
            model.mensajes.last().texto
                .startsWith("⚠️ No hay conexión con el backend (10.0.2.2:9000):")
        )
        assertFalse(model.enviando)
    }

    // ------------------------------------------------- ausencia de respuesta
    @Test
    fun sinRespuestaDelBackendSeMuestraElMensajePorDefecto() {
        servidor.responder(200, """{"mensaje":null,"productos":null}""")
        val model = registrar(ChatbotViewModel())
        model.enviarConsulta("notebook")
        esperar { model.mensajes.size == 3 }

        assertEquals(
            "No hay una respuesta disponible.",
            model.mensajes.last().texto
        )
        assertTrue(model.mensajes.last().productos.isEmpty())
        assertNull(model.mensajeError)
        assertFalse(model.enviando)
    }

    // --------------------------------------------------------- cambios de modo
    @Test
    fun elModoNoCambiaMientrasSeEnvia() {
        servidor.servidor.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpoExito)
                .setHeadersDelay(800, TimeUnit.MILLISECONDS)
        )
        val model = registrar(ChatbotViewModel())
        model.enviarConsulta("teclado")
        assertTrue(model.enviando)
        model.cambiarModo(ModoChatbot.CONSULTAS_TECNICAS)

        assertEquals(
            "El modo no cambia mientras se envía",
            ModoChatbot.RECOMENDACIONES,
            model.modo
        )

        esperar { !model.enviando }
        assertNotNull(model.mensajes)
        assertEquals(3, model.mensajes.size)
    }
}
