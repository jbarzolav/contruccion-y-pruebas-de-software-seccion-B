package com.ejemplo.publicarproducto.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.BusEventosSesion
import com.ejemplo.publicarproducto.model.session.EventoSesion
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.testing.ContenedorDePruebas
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import com.ejemplo.publicarproducto.testing.cancelarAlcance
import com.ejemplo.publicarproducto.testing.esperarCondicion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R4 / R8 - Expiración de sesión centralizada e idempotente.
 *
 * - **R4**: la invalidación de la sesión se hace en un único sitio
 *   (`SesionViewModel`). El resto de ViewModels (por ejemplo `PerfilViewModel`)
 *   solo **publican el evento** `SesionExpirada`; no borran el token por su
 *   cuenta. Así la interfaz nunca queda con un estado de sesión incoherente
 *   ni se duplica la invalidación.
 * - **R8**: la expiración es **idempotente**. `BusEventosSesion` conserva el
 *   último evento (`replay = 1`) porque la expiración puede ocurrir en
 *   segundo plano (por ejemplo, desde el sincronizador de WorkManager). Si la
 *   sesión ya está cerrada, se omite el aviso, la invalidación y la limpieza
 *   de los datos locales.
 */
@RunWith(AndroidJUnit4::class)
class ExpiracionSesionCentralizadaTest {

    private lateinit var aplicacion: Application
    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas

    private val usuarioId = 770001L

    private val viewModels = mutableListOf<ViewModel>()

    private fun <T : ViewModel> registrar(model: T): T {
        viewModels += model
        return model
    }

    /** Eventos del bus de sesión observados durante la prueba. */
    private val eventos = mutableListOf<EventoSesion>()
    private lateinit var alcanceEventos: CoroutineScope

    @Before
    fun setUp() {
        aplicacion = ApplicationProvider.getApplicationContext()
        contexto = aplicacion
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesion = SessionManager(contexto)
        sesion.invalidarSesion()
        servidor = ServidorDePruebas()
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))

        // `BusEventosSesion` es un objeto compartido con `replay = 1`: al
        // suscribirse se entrega el último evento. Se descarta esa entrega
        // inicial para que la prueba solo cuente lo que ocurra aquí.
        alcanceEventos = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        alcanceEventos.launch {
            BusEventosSesion.eventos.collect { eventos += it }
        }
        Thread.sleep(100)
        eventos.clear()
    }

    @After
    fun tearDown() {
        alcanceEventos.cancel()
        eventos.clear()

        viewModels.forEach { it.cancelarAlcance() }
        viewModels.clear()

        ProveedorDatos.fijar(null)
        sesion.invalidarSesion()

        bd.close()
        servidor.cerrar()
    }

    private fun esperar(
        descripcion: String,
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
    }

    @Test
    fun elPerfilPublicaElEventoYNoInvalidaLaSesionPorSuCuenta() {
        sesion.guardarSesion(token = "token-prueba", id = usuarioId, correo = "a@pulgatec.pe")

        // El backend responde 401 al pedir el perfil.
        servidor.servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":401,"error":"Unauthorized","messages":["Token inválido"]}""")
        }

        val model = registrar(PerfilViewModel(aplicacion))
        model.cargarPerfil()

        esperar("el aviso de sesión expirada") {
            eventos.any { it == EventoSesion.SesionExpirada }
        }

        assertTrue(
            "R4 - PerfilViewModel no debe borrar el token: solo publica el evento",
            sesion.haySesion()
        )
        assertEquals(
            "R4 - Solo se publica el evento de expiración",
            1,
            eventos.count { it == EventoSesion.SesionExpirada }
        )
    }

    @Test
    fun elViewModelDeSesionInvalidaLaSesionCuandoLlegaElEvento() {
        sesion.guardarSesion(token = "token-prueba", id = usuarioId, correo = "a@pulgatec.pe")

        val model = registrar(SesionViewModel(aplicacion))
        assertTrue("El usuario debe estar autenticado", model.usuario != null)

        BusEventosSesion.publicar(EventoSesion.SesionExpirada)

        esperar("la invalidación de la sesión") { !sesion.haySesion() }

        assertNull("La sesión queda cerrada", model.usuario)
        assertTrue(
            "El aviso de sesión vencida debe mostrarse",
            model.aviso?.contains("vencido") == true
        )
        assertTrue(
            "Debe publicarse el cierre de sesión",
            eventos.any { it == EventoSesion.SesionCerrada }
        )
    }

    @Test
    fun un401RepetidoNoDuplicaElAvisoNiLaInvalidacion() {
        sesion.guardarSesion(token = "token-prueba", id = usuarioId, correo = "a@pulgatec.pe")

        val model = registrar(SesionViewModel(aplicacion))

        model.manejarError(401)
        esperar("la invalidación de la sesión") { !sesion.haySesion() }
        val avisosTrasLaPrimera = model.aviso
        val cierresTrasLaPrimera = eventos.count { it == EventoSesion.SesionCerrada }

        model.manejarError(401)
        Thread.sleep(300)

        assertEquals(
            "R8 - El aviso no debe cambiar ni duplicarse",
            avisosTrasLaPrimera,
            model.aviso
        )
        assertEquals(
            "R8 - El cierre de sesión no debe publicarse dos veces",
            cierresTrasLaPrimera,
            eventos.count { it == EventoSesion.SesionCerrada }
        )
    }

    @Test
    fun un401SinSesionNoHaceNada() {
        // Sin sesión guardada y sin usuario: la expiración ya se procesó.
        val model = registrar(SesionViewModel(aplicacion))
        assertNull(model.usuario)
        assertFalse(sesion.haySesion())

        model.manejarError(401)
        Thread.sleep(300)

        assertNull(
            "R8 - Sin sesión no hay nada que avisar",
            model.aviso
        )
        assertTrue(
            "R8 - Sin sesión no se publica ningún cierre",
            eventos.none { it == EventoSesion.SesionCerrada }
        )
    }
}
