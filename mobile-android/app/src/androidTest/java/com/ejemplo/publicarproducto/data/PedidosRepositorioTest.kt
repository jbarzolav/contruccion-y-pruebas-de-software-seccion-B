package com.ejemplo.publicarproducto.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.local.Ambitos
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mejora técnica Offline First - pedidos y su detalle.
 *
 * Verifica que los pedidos y sus líneas (con el precio histórico) queden
 * guardados en Room y que se puedan consultar sin conexión.
 */
@RunWith(AndroidJUnit4::class)
class PedidosRepositorioTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var servidor: ServidorDePruebas
    private lateinit var cola: ColaOperacionesRepositorio
    private lateinit var repositorio: PedidosRepositorio

    private val usuarioId = 1L

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        servidor = ServidorDePruebas()
        cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        repositorio = PedidosRepositorio(bd, servidor.api, cola)
    }

    @After
    fun tearDown() {
        bd.close()
        servidor.cerrar()
    }

    private val jsonPedidos = """
        [
          {
            "idPedido": 55,
            "clienteId": 1,
            "fecha": "2024-05-01T10:15:30",
            "total": 120.00,
            "estado": "REGISTRADO",
            "fechaActualizacion": "2024-05-01T10:15:30",
            "detalles": [
              {
                "idProducto": 900,
                "nombreProducto": "Teclado",
                "cantidad": 2,
                "precioUnitario": 60.00,
                "subtotal": 120.00
              }
            ],
            "calificacion": null,
            "resenas": []
          }
        ]
    """.trimIndent()

    private val jsonVentas = """
        [
          {
            "idPedido": 55,
            "fecha": "2024-05-01T10:15:30",
            "estado": "REGISTRADO",
            "fechaActualizacion": "2024-05-01T10:15:30",
            "total": 120.00,
            "productos": [
              {
                "idProducto": 901,
                "nombreProducto": "Mouse",
                "cantidad": 1,
                "precioUnitario": 120.00,
                "subtotal": 120.00
              }
            ],
            "estadosPermitidos": ["EN_PROCESO"]
          }
        ]
    """.trimIndent()

    @Test
    fun refrescarGuardaLosPedidosYSusLineasEnRoom() = runTest {
        servidor.responder(200, jsonPedidos)

        val resultado = repositorio.refrescarCompras(usuarioId)
        assertTrue(resultado is ResultadoApi.Exito)

        val pedidos = repositorio.observarCompras(usuarioId).first()
        assertEquals(1, pedidos.size)
        assertEquals(55L, pedidos.first().idPedido)
        assertEquals("120.00", pedidos.first().total?.toPlainString())

        val detalle = repositorio.observarDetalle(usuarioId, 55L).first()
        assertEquals(1, detalle!!.detalles!!.size)
        assertEquals("60.00", detalle.detalles!!.first().precioUnitario?.toPlainString())
        assertEquals("Teclado", detalle.detalles!!.first().nombreProducto)
    }

    @Test
    fun losPedidosSeConsultanSinConexion() = runTest {
        servidor.responder(200, jsonPedidos)
        repositorio.refrescarCompras(usuarioId)

        servidor.apagar()

        val pedidos = repositorio.observarCompras(usuarioId).first()
        assertEquals(1, pedidos.size)
        assertEquals(55L, pedidos.first().idPedido)

        val detalle = repositorio.observarDetalle(usuarioId, 55L).first()
        assertNotNullDetalle(detalle)
    }

    @Test
    fun elDetalleDeUnPedidoNoSeMezclaConElDeOtroAmbito() = runTest {
        servidor.responder(200, jsonPedidos)
        repositorio.refrescarCompras(usuarioId)

        servidor.responder(200, jsonVentas)
        repositorio.refrescarVentas(usuarioId)

        val compras = repositorio.observarCompras(usuarioId).first()
        val received = repositorio.observarVentas(usuarioId).first()

        assertEquals("Teclado", compras.first().detalles!!.first().nombreProducto)
        assertEquals("Mouse", received.pedidos.first().productos.first().nombreProducto)
        assertEquals(1, bd.pedidoDao().listarDetalles(usuarioId, 55L, Ambitos.COMPRAS).size)
        assertEquals(1, bd.pedidoDao().listarDetalles(usuarioId, 55L, Ambitos.VENTAS).size)
    }

    @Test
    fun laPropuestaDeEstadoNoPisaElEstadoOficial() = runTest {
        // El vendedor descarga sus pedidos: el estado oficial es REGISTRADO.
        servidor.responder(200, jsonVentas)
        repositorio.refrescarVentas(usuarioId)

        // Propone el siguiente estado: se guarda solo como propuesta local.
        repositorio.registrarEstadoPendienteLocal(usuarioId, 55L, Ambitos.VENTAS, "EN_PROCESO")

        val pedido = bd.pedidoDao().buscarPedido(usuarioId, 55L, Ambitos.VENTAS)
        assertNotNull(pedido)
        assertEquals("EN_PROCESO", pedido!!.estadoPendienteLocal)
        // El estado oficial del servidor no se toca hasta que lo confirme.
        assertEquals("REGISTRADO", pedido.estado)
    }

    private fun assertNotNullDetalle(detalle: Any?) {
        assertTrue("El detalle debe seguir disponible sin conexión", detalle != null)
    }
}
