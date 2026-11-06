package com.ejemplo.publicarproducto.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.data.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.data.ContenedorDatos
import com.ejemplo.publicarproducto.data.MonitorConectividad
import com.ejemplo.publicarproducto.data.NotificacionesRepositorio
import com.ejemplo.publicarproducto.data.PedidosRepositorio
import com.ejemplo.publicarproducto.data.ProveedorDatos
import com.ejemplo.publicarproducto.local.Ambitos
import com.ejemplo.publicarproducto.local.EntidadMetadatoSincronizacion
import com.ejemplo.publicarproducto.local.EntidadNotificacion
import com.ejemplo.publicarproducto.local.EntidadPedido
import com.ejemplo.publicarproducto.local.EstadosOperacion
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.local.RecursosSincronizacion
import com.ejemplo.publicarproducto.local.TiposOperacion
import com.ejemplo.publicarproducto.session.SessionManager
import com.ejemplo.publicarproducto.sync.ServicioSincronizacion
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mejora técnica Offline First - aislamiento por usuario y cierre de sesión.
 *
 * Verifica que la información local de un usuario no sea visible para otro y
 * que al cerrar sesión se borre del dispositivo toda la información local
 * (notificaciones, pedidos, cola de pendientes y metadatos) para que no
 * pueda verla nadie más.
 */
@RunWith(AndroidJUnit4::class)
class CierreSesionDatosLocalesTest {

    private lateinit var contexto: Context
    private lateinit var aplicacion: Application
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var cola: ColaOperacionesRepositorio
    private lateinit var servidor: ServidorDePruebas

    private val usuarioId = 880001L
    private val otroUsuarioId = 880002L

    @Before
    fun setUp() {
        aplicacion = ApplicationProvider.getApplicationContext()
        contexto = aplicacion
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        servidor = ServidorDePruebas()
        sesion = SessionManager(contexto)
        cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
    }

    @After
    fun tearDown() {
        sesion.invalidarSesion()
        ProveedorDatos.fijar(null)
        bd.close()
        servidor.cerrar()
    }

    private fun contenedor(): ContenedorDatos {
        val notificaciones = NotificacionesRepositorio(bd, servidor.api, cola)
        val pedidos = PedidosRepositorio(bd, servidor.api, cola)
        return ContenedorDatos(
            contexto = contexto,
            bd = bd,
            cola = cola,
            notificaciones = notificaciones,
            pedidos = pedidos,
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
                sesion = sesion
            )
        )
    }

    private suspend fun guardaDatosDelUsuario(id: Long) {
        bd.notificacionDao().guardarTodas(
            listOf(
                EntidadNotificacion(
                    usuarioId = id,
                    idNotificacion = 10L,
                    tipo = "NUEVO_MENSAJE",
                    resumen = "Aviso de $id",
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
                    usuarioId = id,
                    idPedido = 55L,
                    ambito = Ambitos.COMPRAS,
                    clienteId = id,
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
        cola.encolar(
            usuarioId = id,
            tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "10",
            payload = null,
            claveUnica = "notificacion:10"
        )
        bd.metadatoSincronizacionDao().guardar(
            EntidadMetadatoSincronizacion(
                usuarioId = id,
                recurso = RecursosSincronizacion.OPERACIONES,
                fechaUltimaSincronizacion = 1L,
                estado = "SINCRONIZADO",
                mensaje = "Sincronizado"
            )
        )
    }

    @Test
    fun losDatosDeUnUsuarioNoSonVisiblesPorOtro() = runTest {
        guardaDatosDelUsuario(usuarioId)

        assertNull(bd.notificacionDao().buscar(otroUsuarioId, 10L))
        assertNull(bd.pedidoDao().buscarPedido(otroUsuarioId, 55L, Ambitos.COMPRAS))
        assertTrue(cola.observarPendientes(otroUsuarioId).first().isEmpty())
        assertNull(bd.metadatoSincronizacionDao().buscar(otroUsuarioId, RecursosSincronizacion.OPERACIONES))
    }

    @Test
    fun alCerrarSesionSeBorraTodaLaInformacionLocalDelUsuario() = runTest {
        sesion.guardarSesion(token = "token-prueba", id = usuarioId, correo = "prueba@pulgatec.pe")
        guardaDatosDelUsuario(usuarioId)
        ProveedorDatos.fijar(contenedor())

        val model = SesionViewModel(aplicacion)
        model.volverAlLogin()

        // La limpieza se ejecuta en el ambito de la vista: se espera a que termine.
        val limite = System.currentTimeMillis() + 15_000L
        while (System.currentTimeMillis() < limite) {
            if (bd.notificacionDao().buscar(usuarioId, 10L) == null &&
                cola.observarPendientes(usuarioId).first().isEmpty()
            ) break
            delay(50)
        }

        assertNull("El aviso no debe quedar en el dispositivo", bd.notificacionDao().buscar(usuarioId, 10L))
        assertNull("El pedido no debe quedar en el dispositivo", bd.pedidoDao().buscarPedido(usuarioId, 55L, Ambitos.COMPRAS))
        assertTrue("La cola no debe quedar en el dispositivo", cola.observarPendientes(usuarioId).first().isEmpty())
        assertNull(
            "Los metadatos no deben quedar en el dispositivo",
            bd.metadatoSincronizacionDao().buscar(usuarioId, RecursosSincronizacion.OPERACIONES)
        )
        // La sesion queda invalidada.
        assertTrue(!sesion.haySesion())
    }

    @Test
    fun elHistorialDeOperacionesConservaLoConfirmado() = runTest {
        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "10",
            payload = null,
            claveUnica = "notificacion:10"
        )
        val pendiente = cola.observarPendientes(usuarioId).first().single()
        cola.confirmar(pendiente.id, "Confirmada por el servidor.")

        val historial = cola.observarTodas(usuarioId).first()
        assertEquals(1, historial.size)
        assertEquals(EstadosOperacion.CONFIRMADA, historial.single().estado)
        assertEquals("Confirmada por el servidor.", historial.single().ultimoMensajeError)
        // Una operacion confirmada ya no espera confirmacion del servidor.
        assertTrue(cola.observarPendientes(usuarioId).first().isEmpty())
    }
}
