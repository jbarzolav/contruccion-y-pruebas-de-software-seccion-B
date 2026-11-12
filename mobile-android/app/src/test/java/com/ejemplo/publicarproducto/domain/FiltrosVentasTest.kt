package com.ejemplo.publicarproducto.domain

import com.ejemplo.publicarproducto.model.DetallePedidoResponse
import com.ejemplo.publicarproducto.model.PedidoVendedorResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class FiltrosVentasTest {
    private val pedidos = listOf(
        pedido(1, "2025-02-01T10:00:00", "EN_PROCESO", 8),
        pedido(2, "2025-02-02T10:00:00", "ENTREGADO", 9),
        pedido(3, "2025-02-03T10:00:00", "REGISTRADO", 8)
    )

    @Test fun fechasIncluyenAmbosLimites() {
        assertEquals(listOf(1L, 2L), FiltrosVentas(desde = "2025-02-01", hasta = "2025-02-02").filtrar(pedidos).map { it.idPedido })
    }

    @Test fun estadoIgnoraMayusculas() {
        assertEquals(listOf(1L), FiltrosVentas(estado = "en_proceso").filtrar(pedidos).map { it.idPedido })
    }

    @Test fun filtraPorProducto() {
        assertEquals(listOf(2L), FiltrosVentas(productoId = "9").filtrar(pedidos).map { it.idPedido })
    }

    @Test fun validaFechasInvertidas() {
        assertEquals("La fecha inicial no puede superar la fecha final.", FiltrosVentas(desde = "2025-02-03", hasta = "2025-02-01").errorDeValidacion())
    }

    @Test fun rechazaFechaNoValida() {
        assertEquals("Usa fechas reales con formato AAAA-MM-DD.", FiltrosVentas(desde = "2025-02-30").errorDeValidacion())
        assertNull(FiltrosVentas().errorDeValidacion())
    }

    private fun pedido(id: Long, fecha: String, estado: String, producto: Long) = PedidoVendedorResponse(
        idPedido = id,
        fecha = fecha,
        estado = estado,
        fechaActualizacion = null,
        total = BigDecimal.TEN,
        productos = listOf(DetallePedidoResponse(producto, "Producto", 1, BigDecimal.TEN, BigDecimal.TEN))
    )
}
