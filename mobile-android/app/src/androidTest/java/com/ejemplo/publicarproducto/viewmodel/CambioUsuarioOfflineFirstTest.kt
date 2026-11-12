package com.ejemplo.publicarproducto.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.Ambitos
import com.ejemplo.publicarproducto.model.local.EntidadNotificacion
import com.ejemplo.publicarproducto.model.local.EntidadPedido
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.testing.ContenedorDePruebas
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import com.ejemplo.publicarproducto.testing.cancelarAlcance
import com.ejemplo.publicarproducto.testing.esperarCondicion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R2 - Cambio de usuario en los ViewModels Offline First.
 *
 * Antes, la identidad del usuario se capturaba en el `init` del ViewModel y
 * quedaba congelada: si el usuario cerraba sesión y entraba otra cuenta en
 * la misma `Activity`, los collectors seguían observando los datos del
 * usuario anterior y la pantalla mostraba información ajena.
 *
 * Ahora la identidad llega de `SessionManager.usuarioIdFlow`:
 *
 * 1. al cambiar de usuario se cancelan los collectors del anterior;
 * 2. se limpia el estado de presentación del usuario anterior;
 * 3. se inician las observaciones del usuario nuevo.
 *
 * La prueba usa Room real (en memoria) y la sesión real, así que comprueba
 * el camino completo: View → ViewModel → Repositorio → Room.
 */
@RunWith(AndroidJUnit4::class)
class CambioUsuarioOfflineFirstTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas

    private val usuarioA = 990001L
    private val usuarioB = 990002L

    private val viewModels = mutableListOf<ViewModel>()

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

    private fun esperar(
        descripcion: String,
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
    }

    /** Guarda un pedido y un aviso de los dados en la copia local. */
    private fun guardaDatos(usuarioId: Long, idPedido: Long, idNotificacion: Long) =
        runBlocking {
            bd.notificacionDao().guardarTodas(
                listOf(
                    EntidadNotificacion(
                        usuarioId = usuarioId,
                        idNotificacion = idNotificacion,
                        tipo = "NUEVO_MENSAJE",
                        resumen = "Aviso de $usuarioId",
                        fecha = "2024-05-01T10:15:30.000000000",
                        estado = "PENDIENTE",
                        idPedido = null,
                        enlace = "/consultas?c=1",
                        idConversacion = 7,
                        fechaLectura = null,
                        fechaSincronizacion = 1L,
                        pendienteLecturaLocal = false
                    )
                )
            )
            bd.pedidoDao().guardarPedidos(
                listOf(
                    EntidadPedido(
                        usuarioId = usuarioId,
                        idPedido = idPedido,
                        ambito = Ambitos.COMPRAS,
                        clienteId = usuarioId,
                        fecha = "2024-05-01T10:15:30.000000000",
                        total = "120.00",
                        estado = "REGISTRADO",
                        fechaActualizacion = null,
                        estadosPermitidos = null,
                        estadoPendienteLocal = null,
                        fechaSincronizacion = 1L
                    )
                )
            )
        }

    @Test
    fun alCambiarDeUsuarioSeLimpiaElEstadoYSeObservanLosDatosDelNuevo() {
        // Sesión del usuario A antes de crear el ViewModel.
        sesion.guardarSesion(token = "token-a", id = usuarioA, correo = "a@pulgatec.pe")
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))

        val model = registrar(MisPedidosViewModel())

        guardaDatos(usuarioA, idPedido = 501L, idNotificacion = 11L)

        esperar("los datos del usuario A") { model.pedidos.isNotEmpty() }
        assertEquals(501L, model.pedidos.single().idPedido)
        assertEquals(1, model.notificaciones.size)

        // El usuario cierra sesión y entra otra cuenta EN LA MISMA Activity.
        sesion.guardarSesion(token = "token-b", id = usuarioB, correo = "b@pulgatec.pe")

        esperar("que se limpie el estado del usuario A") {
            model.pedidos.isEmpty() && model.notificaciones.isEmpty()
        }

        // Y ahora se observan los datos del usuario B.
        guardaDatos(usuarioB, idPedido = 502L, idNotificacion = 12L)

        esperar("los datos del usuario B") { model.pedidos.isNotEmpty() }
        assertEquals(
            "La pantalla debe mostrar los pedidos del usuario activo",
            502L,
            model.pedidos.single().idPedido
        )
        assertEquals(12L, model.notificaciones.single().idNotificacion)
    }

    @Test
    fun alCerrarSesionSeLimpiaElEstadoDelUsuario() {
        sesion.guardarSesion(token = "token-a", id = usuarioA, correo = "a@pulgatec.pe")
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))

        val model = registrar(MisPedidosViewModel())

        guardaDatos(usuarioA, idPedido = 501L, idNotificacion = 11L)
        esperar("los datos del usuario A") { model.pedidos.isNotEmpty() }

        sesion.invalidarSesion()

        esperar("que se limpie el estado al cerrar sesión") {
            model.pedidos.isEmpty() && model.notificaciones.isEmpty()
        }

        // Con la sesión cerrada no se vuelve a observar información protegida.
        guardaDatos(usuarioA, idPedido = 503L, idNotificacion = 13L)
        Thread.sleep(500)

        assertTrue(
            "Sin sesión no se muestran pedidos",
            model.pedidos.isEmpty()
        )
        assertTrue(
            "Sin sesión no se muestran avisos",
            model.notificaciones.isEmpty()
        )
        assertFalse(sesion.haySesion())
    }
}
