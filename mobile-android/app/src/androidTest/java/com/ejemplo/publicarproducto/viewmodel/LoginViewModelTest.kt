package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.repository.AuthRepositorio
import com.ejemplo.publicarproducto.model.repository.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.model.di.ContenedorDatos
import com.ejemplo.publicarproducto.model.repository.MetricasRepositorio
import com.ejemplo.publicarproducto.model.sync.MonitorConectividad
import com.ejemplo.publicarproducto.model.repository.NotificacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.PedidosRepositorio
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.repository.UmbralStockRepositorio
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.model.sync.ServicioSincronizacion
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.ejemplo.publicarproducto.testing.esperarCondicion
import androidx.lifecycle.ViewModel
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo A - LoginViewModel (HU 21).
 *
 * Comprueba que el ViewModel ya no habla con Retrofit: valida en local,
 * delega en AuthRepositorio y solo traduce el resultado a estado de UI.
 * La sesión la persiste el repositorio, nunca la pantalla.
 */
@RunWith(AndroidJUnit4::class)
class LoginViewModelTest {

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
        ProveedorDatos.fijar(contenedor())
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

    private fun contenedor(): ContenedorDatos {
        val cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        val notificaciones = NotificacionesRepositorio(bd, servidor.api, cola)
        val pedidos = PedidosRepositorio(bd, servidor.api, cola)
        val metricas = MetricasRepositorio(bd, servidor.api)
        val umbrales = UmbralStockRepositorio(bd, servidor.api, cola, notificaciones)
        return ContenedorDatos(
            contexto = contexto,
            bd = bd,
            cola = cola,
            notificaciones = notificaciones,
            pedidos = pedidos,
            metricas = metricas,
            umbrales = umbrales,
            metadatos = bd.metadatoSincronizacionDao(),
            conectividad = MonitorConectividad(contexto),
            sesion = sesion,
            sincronizacion = ServicioSincronizacion(
                bd = bd,
                api = servidor.api,
                cola = cola,
                notificaciones = notificaciones,
                pedidos = pedidos,
                metadatos = bd.metadatoSincronizacionDao(),
                sesion = sesion,
                umbrales = umbrales
            ),
            // El ViewModel bajo prueba debe hablar con el servidor de pruebas.
            auth = AuthRepositorio(servidor.authApi, sesion)
        )
    }

    /** Espera en tiempo real a que el ViewModel termine (corre en el hilo principal). */
    private fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
    }

    private fun LoginViewModel.enviar(correo: String, password: String) {
        cambiarCorreo(correo)
        cambiarPassword(password)
        iniciarSesion()
    }

    @Test
    fun laValidacionLocalNoLlamaAlBackend() {
        val model = registrar(LoginViewModel())
        model.enviar("", "")

        assertEquals("El correo es obligatorio", model.errorCorreo)
        assertEquals("La contraseña es obligatoria", model.errorPassword)
        assertTrue(model.uiState is LoginUiState.Initial)
        assertEquals(0, servidor.peticionesRecibidas())
    }

    @Test
    fun loginExitosoEntraAlFlujoYPersisteLaSesion() {
        servidor.responder(
            200,
            """{"token":"tok-1","id":5,"correo":"ana@pulgatec.pe","rol":"CLIENTE","permisos":["COMPRAR"]}"""
        )
        val model = registrar(LoginViewModel())
        model.enviar("ana@pulgatec.pe", "secreta123")
        esperar { model.uiState is LoginUiState.Success }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is LoginUiState.Success)
        // La persistencia la hizo AuthRepositorio, no la pantalla.
        assertEquals("tok-1", sesion.obtenerToken())
        assertEquals(5L, sesion.obtenerUsuarioId())
        assertEquals("ana@pulgatec.pe", sesion.obtenerUsuario()?.correo)
        assertEquals(setOf("COMPRAR"), sesion.obtenerUsuario()?.permisos)
    }

    @Test
    fun loginIncorrectoMuestraElMensajeGenericoYSinSesion() {
        servidor.responderError(401, "Usuario no encontrado")
        val model = registrar(LoginViewModel())
        model.enviar("noexiste@pulgatec.pe", "otracosa1")
        esperar { model.uiState is LoginUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is LoginUiState.Error)
        assertEquals(
            "Correo o contraseña incorrectos.",
            (estado as LoginUiState.Error).mensaje
        )
        assertFalse("No debe crearse sesión", sesion.haySesion())
    }

    @Test
    fun sinConexionMuestraElMensajeDeRed() {
        servidor.apagar()
        val model = registrar(LoginViewModel())
        model.enviar("ana@pulgatec.pe", "secreta123")
        esperar { model.uiState is LoginUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is LoginUiState.Error)
        assertEquals(
            "No hay conexión con el servidor.",
            (estado as LoginUiState.Error).mensaje
        )
    }

    @Test
    fun elDobleToqueNoEnviaDosPeticiones() {
        servidor.responder(
            200,
            """{"token":"tok-1","id":5,"correo":"ana@pulgatec.pe","rol":"CLIENTE","permisos":[]}"""
        )
        val model = registrar(LoginViewModel())
        model.enviar("ana@pulgatec.pe", "secreta123")
        // Segundo toque inmediato: el guard sincrónico debe cortarlo.
        model.iniciarSesion()
        esperar { model.uiState is LoginUiState.Success }

        assertEquals(1, servidor.peticionesRecibidas())
    }
}
