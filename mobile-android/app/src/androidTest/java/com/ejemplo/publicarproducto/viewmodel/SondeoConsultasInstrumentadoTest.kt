package com.ejemplo.publicarproducto.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.testing.ContenedorDePruebas
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import com.ejemplo.publicarproducto.testing.cancelarAlcance
import com.ejemplo.publicarproducto.testing.esperarCondicion
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R1/R3 - Sondeo de HU39 contra el servidor real de pruebas.
 *
 * Esta prueba instrumentada verifica, sobre HTTP de verdad (MockWebServer) y
 * con la aplicación en marcha, el contrato del único responsable del sondeo
 * de `GET /api/conversaciones`:
 *
 * - se hace **una petición por intervalo** (nada más);
 * - `iniciarSondeo()` dos veces **no duplica** las peticiones;
 * - `detenerSondeo()` **cancela** el trabajo: no queda ninguna petición viva
 *   (esto es lo que evita las peticiones en segundo plano);
 * - al destruir el ViewModel el sondeo se cancela también.
 *
 * El intervalo funcional es de 15 s; la prueba lo acorta (el intervalo es
 * `internal` y solo lo modifican las pruebas) para no tardar medio minuto.
 */
@RunWith(AndroidJUnit4::class)
class SondeoConsultasInstrumentadoTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas

    private val viewModels = mutableListOf<ViewModel>()

    private fun <T : ViewModel> registrar(model: T): T {
        viewModels += model
        return model
    }

    /** Intervalo acortado para la prueba (el de producción son 15 s). */
    private val intervalo = 1_000L

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesion = SessionManager(contexto)
        sesion.invalidarSesion()
        servidor = ServidorDePruebas()
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))

        // El servidor responde siempre con la lista de conversaciones: el
        // sondeo puede repetirse sin quedarse esperando respuestas encoladas.
        servidor.servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val cuerpo = when {
                    request.path == "/api/conversaciones/configuracion" ->
                        """{"longitudMaxima":1000}"""

                    request.path == "/api/conversaciones" ->
                        """[{"idConversacion":7,"productoId":3,"productoNombre":"Teclado",
                              "productoImagenUrl":null,"productoDisponible":true,
                              "fechaCreacion":"2026-01-01T10:00:00",
                              "ultimoMensaje":null}]"""

                    else -> """{"status":404,"error":"Not Found","messages":["Ruta inesperada"]}"""
                }
                val codigo = if (cuerpo.contains("\"status\":404")) 404 else 200
                return MockResponse()
                    .setResponseCode(codigo)
                    .setHeader("Content-Type", "application/json")
                    .setBody(cuerpo)
            }
        }
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.cancelarAlcance() }
        viewModels.clear()

        ProveedorDatos.fijar(null)
        sesion.invalidarSesion()

        bd.close()
        servidor.cerrar()
    }

    /** Peticiones recibidas contra la lista de conversaciones. */
    private fun peticionesDeConversaciones(): Int =
        servidor.rutasRecibidas().count { it == "/api/conversaciones" }

    private fun esperar(
        descripcion: String,
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
    }

    @Test
    fun elSondeoHaceUnaPeticionPorIntervaloYSeDetieneAlPararlo() {
        val model = registrar(ConsultasViewModel())
        model.intervaloSondeoMs = intervalo

        model.iniciarSondeo()
        assertTrue("El sondeo debe estar activo", model.sondeoActivo())

        // 1) Llega la primera petición tras el primer intervalo.
        esperar("la primera petición del sondeo") { peticionesDeConversaciones() >= 1 }

        // 2) En una ventana de tres intervalos hay UNA petición por intervalo.
        val antes = peticionesDeConversaciones()
        Thread.sleep(intervalo * 3)
        val delta = peticionesDeConversaciones() - antes
        assertTrue(
            "Debe haber una petición por intervalo (se esperaban ~3 y llegaron $delta)",
            delta in 2..4
        )

        // 3) Activar el sondeo dos veces NO lo duplica.
        model.iniciarSondeo()
        val antesDuplicado = peticionesDeConversaciones()
        Thread.sleep(intervalo * 3)
        val deltaDuplicado = peticionesDeConversaciones() - antesDuplicado
        assertTrue(
            "Un único responsable del sondeo: no puede haber más peticiones por " +
                "intervalo (llegaron $deltaDuplicado)",
            deltaDuplicado in 2..4
        )

        // 4) Detener el sondeo cancela el trabajo: no queda ninguna petición.
        model.detenerSondeo()
        assertFalse("Tras detener, el sondeo no está activo", model.sondeoActivo())

        val antesDetenido = peticionesDeConversaciones()
        Thread.sleep(intervalo * 3)
        assertEquals(
            "Tras detener el sondeo no debe haber ninguna petición HTTP",
            antesDetenido,
            peticionesDeConversaciones()
        )
    }

    @Test
    fun elSondeoSeCancelaAlDestruirElViewModel() {
        val model = ConsultasViewModel()
        model.intervaloSondeoMs = intervalo

        // Se usa el ciclo de vida real de Android: `ViewModelStore.clear()`
        // es lo que llama a `onCleared()` cuando la pantalla desaparece.
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
        }
        val provider = ViewModelProvider.create(owner, factory)
        val delProvider = provider[ConsultasViewModel::class.java]
        registrar(delProvider)

        delProvider.iniciarSondeo()
        esperar("la primera petición del sondeo") { peticionesDeConversaciones() >= 1 }

        store.clear()
        assertFalse("El sondeo se cancela con el ViewModel", delProvider.sondeoActivo())

        val antes = peticionesDeConversaciones()
        Thread.sleep(intervalo * 3)
        assertEquals(
            "No debe quedar ninguna petición viva tras destruir el ViewModel",
            antes,
            peticionesDeConversaciones()
        )
    }
}
