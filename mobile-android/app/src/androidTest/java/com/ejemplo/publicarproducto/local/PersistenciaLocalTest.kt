package com.ejemplo.publicarproducto.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mejora técnica Offline First - persistencia de la copia local.
 *
 * Verifica que Room guarde y devuelva notificaciones, pedidos y detalles, que
 * los datos queden isolados por usuario y que la información sobreviva al
 * cierre de la aplicación (base en disco).
 */
@RunWith(AndroidJUnit4::class)
class PersistenciaLocalTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
    }

    @After
    fun tearDown() {
        bd.close()
    }

    private fun notificacion(usuarioId: Long, id: Long, estado: String = "PENDIENTE") =
        EntidadNotificacion(
            usuarioId = usuarioId,
            idNotificacion = id,
            tipo = "NUEVO_MENSAJE",
            resumen = "Aviso $id",
            fecha = "2024-05-01T10:15:30.000000000",
            estado = estado,
            idPedido = null,
            enlace = "/consultas?c=1",
            idConversacion = 7,
            fechaLectura = null,
            fechaSincronizacion = 1000L,
            pendienteLecturaLocal = false
        )

    @Test
    fun guardaYLeeNotificaciones() = runTest {
        bd.notificacionDao().guardarTodas(listOf(notificacion(1L, 10L)))

        val guardada = bd.notificacionDao().buscar(1L, 10L)
        assertNotNull(guardada)
        assertEquals("NUEVO_MENSAJE", guardada!!.tipo)
        assertEquals("PENDIENTE", guardada.estado)
        assertEquals(1L, bd.notificacionDao().contarPendientesAhora(1L))
    }

    @Test
    fun guardaYLeePedidosYDetalles() = runTest {
        bd.pedidoDao().guardarPedidos(
            listOf(
                EntidadPedido(
                    usuarioId = 1L,
                    idPedido = 55L,
                    ambito = Ambitos.COMPRAS,
                    clienteId = 1L,
                    fecha = "2024-05-01T10:15:30.000000000",
                    total = "120.00",
                    estado = "REGISTRADO",
                    fechaActualizacion = "2024-05-01T10:15:30.000000000",
                    estadosPermitidos = null,
                    estadoPendienteLocal = null,
                    fechaSincronizacion = 1L
                )
            )
        )
        bd.pedidoDao().insertarDetalles(
            listOf(
                EntidadDetallePedido(
                    usuarioId = 1L,
                    idPedido = 55L,
                    ambito = Ambitos.COMPRAS,
                    idProducto = 900L,
                    nombreProducto = "Teclado",
                    cantidad = 2,
                    precioUnitario = "60.00",
                    subtotal = "120.00",
                    fechaSincronizacion = 1L
                )
            )
        )

        val pedido = bd.pedidoDao().buscarPedido(1L, 55L, Ambitos.COMPRAS)
        assertNotNull(pedido)
        assertEquals("120.00", pedido!!.total)

        val detalles = bd.pedidoDao().listarDetalles(1L, 55L, Ambitos.COMPRAS)
        assertEquals(1, detalles.size)
        assertEquals("60.00", detalles.first().precioUnitario)
    }

    @Test
    fun losDatosDeUnUsuarioNoSonVisiblesPorOtro() = runTest {
        bd.notificacionDao().guardarTodas(listOf(notificacion(1L, 10L)))
        bd.pedidoDao().guardarPedidos(
            listOf(
                EntidadPedido(
                    usuarioId = 1L,
                    idPedido = 55L,
                    ambito = Ambitos.COMPRAS,
                    clienteId = 1L,
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

        assertNull(bd.notificacionDao().buscar(2L, 10L))
        assertNull(bd.pedidoDao().buscarPedido(2L, 55L, Ambitos.COMPRAS))
        assertEquals(0L, bd.notificacionDao().contarPendientesAhora(2L))
    }

    @Test
    fun laLecturaLocalSeDistingueDeLaConfirmadaPorElServidor() = runTest {
        bd.notificacionDao().guardarTodas(listOf(notificacion(1L, 10L)))

        bd.notificacionDao().marcarLecturaLocal(1L, 10L, "2024-05-02T08:00:00.000000000")
        val local = bd.notificacionDao().buscar(1L, 10L)
        assertEquals("LEIDA", local!!.estado)
        assertTrue(local.pendienteLecturaLocal)

        bd.notificacionDao().aplicarEstadoServidor(1L, 10L, "LEIDA", "2024-05-02T08:00:00.000000000")
        val confirmado = bd.notificacionDao().buscar(1L, 10L)
        assertEquals("LEIDA", confirmado!!.estado)
        assertTrue(!confirmado.pendienteLecturaLocal)
    }

    @Test
    fun elServidorPrevaleceYLaLecturaLocalSeRevierte() = runTest {
        bd.notificacionDao().guardarTodas(listOf(notificacion(1L, 10L)))
        bd.notificacionDao().marcarLecturaLocal(1L, 10L, "2024-05-02T08:00:00.000000000")

        bd.notificacionDao().revertirLecturaLocal(1L, 10L)
        val revertida = bd.notificacionDao().buscar(1L, 10L)
        assertEquals("PENDIENTE", revertida!!.estado)
        assertTrue(!revertida.pendienteLecturaLocal)
    }

    @Test
    fun laColaRegistraIdTipoRecursoDatosFechaEstadoEIntentos() = runTest {
        val id = bd.operacionPendienteDao().insertar(
            EntidadOperacionPendiente(
                usuarioId = 1L,
                tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
                recursoId = "10",
                payload = null,
                claveUnica = "notificacion:10",
                fechaCreacion = 1234L,
                fechaUltimoIntento = null,
                estado = EstadosOperacion.PENDIENTE,
                intentos = 0,
                ultimoMensajeError = null
            )
        )
        assertTrue(id > 0)

        val pendiente = bd.operacionPendienteDao().listarPendientes(1L).single()
        assertEquals(TiposOperacion.MARCAR_NOTIFICACION_LEIDA, pendiente.tipoOperacion)
        assertEquals("10", pendiente.recursoId)
        assertEquals(1234L, pendiente.fechaCreacion)
        assertEquals(EstadosOperacion.PENDIENTE, pendiente.estado)
        assertEquals(0, pendiente.intentos)

        bd.operacionPendienteDao().registrarIntento(id, EstadosOperacion.PENDIENTE, 2000L, "sin red")
        val reintentada = bd.operacionPendienteDao().listarPendientes(1L).single()
        assertEquals(1, reintentada.intentos)
        assertEquals(2000L, reintentada.fechaUltimoIntento)
        assertEquals("sin red", reintentada.ultimoMensajeError)
    }

    @Test
    fun lasOperacionesRechazadasNoVuelvenALaColaDePendientes() = runTest {
        val id = bd.operacionPendienteDao().insertar(
            EntidadOperacionPendiente(
                usuarioId = 1L,
                tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
                recursoId = "10",
                payload = null,
                claveUnica = "notificacion:10",
                fechaCreacion = 1L,
                fechaUltimoIntento = null,
                estado = EstadosOperacion.PENDIENTE,
                intentos = 0,
                ultimoMensajeError = null
            )
        )
        bd.operacionPendienteDao().actualizarEstado(id, EstadosOperacion.RECHAZADA, "rechazado")

        assertTrue(bd.operacionPendienteDao().listarPendientes(1L).isEmpty())
        assertEquals(1, bd.operacionPendienteDao().observarTodas(1L).first().size)
    }

    @Test
    fun laInformacionPersisteAlCerrarLaAplicacion() = runTest {
        val nombre = "pulgatec_prueba_persistencia.db"
        contexto.deleteDatabase(nombre)

        val primera = PulgaTecDatabase.crearEnDisco(contexto, nombre)
        primera.notificacionDao().guardarTodas(listOf(notificacion(1L, 10L)))
        primera.operacionPendienteDao().insertar(
            EntidadOperacionPendiente(
                usuarioId = 1L,
                tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
                recursoId = "10",
                payload = null,
                claveUnica = "notificacion:10",
                fechaCreacion = 1L,
                fechaUltimoIntento = null,
                estado = EstadosOperacion.PENDIENTE,
                intentos = 0,
                ultimoMensajeError = null
            )
        )
        // Se cierra la base como si la aplicación se cerrara.
        primera.close()

        val segunda = PulgaTecDatabase.crearEnDisco(contexto, nombre)
        val aviso = segunda.notificacionDao().buscar(1L, 10L)
        assertNotNull("El aviso debe seguir en la base tras reabrirla", aviso)
        assertEquals(1, segunda.operacionPendienteDao().listarPendientes(1L).size)
        segunda.close()
        contexto.deleteDatabase(nombre)
    }
}
