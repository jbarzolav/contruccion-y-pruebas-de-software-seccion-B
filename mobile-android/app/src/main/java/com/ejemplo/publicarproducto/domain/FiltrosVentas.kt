package com.ejemplo.publicarproducto.domain

import com.ejemplo.publicarproducto.model.PedidoVendedorResponse
import java.time.LocalDate

/**
 * Filtros locales para las ventas recibidas; no inicia consultas remotas.
 *
 * HU 56 - Filtra los pedidos y los productos del vendedor autenticado: es el
 * mismo criterio que aplica el backend (`PedidosVendedorService`) para
 * mostrar únicamente las ventas del vendedor de la sesión.
 *
 * HU 48 / HU 49 - Pantalla de pedidos recibidos del vendedor.
 */
data class FiltrosVentas(
    val estado: String = "",
    val desde: String = "",
    val hasta: String = "",
    val productoId: String = ""
) {
    fun errorDeValidacion(): String? = when {
        !fechaValida(desde) || !fechaValida(hasta) -> "Usa fechas reales con formato AAAA-MM-DD."
        desde.isNotBlank() && hasta.isNotBlank() && desde > hasta -> "La fecha inicial no puede superar la fecha final."
        else -> null
    }

    fun filtrar(pedidos: List<PedidoVendedorResponse>): List<PedidoVendedorResponse> = pedidos.filter { pedido ->
        val fecha = pedido.fecha?.take(10)
        (estado.isBlank() || pedido.estado.equals(estado, ignoreCase = true)) &&
            (desde.isBlank() || (fecha != null && fecha >= desde)) &&
            (hasta.isBlank() || (fecha != null && fecha <= hasta)) &&
            (productoId.isBlank() || pedido.productos.any { it.idProducto?.toString() == productoId })
    }

    private fun fechaValida(fecha: String): Boolean = fecha.isBlank() ||
        (fecha.length == 10 && runCatching { LocalDate.parse(fecha) }.isSuccess)
}
