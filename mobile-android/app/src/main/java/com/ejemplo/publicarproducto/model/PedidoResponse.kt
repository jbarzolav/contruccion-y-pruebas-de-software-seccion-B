package com.ejemplo.publicarproducto.model

import java.math.BigDecimal

/**
 * HU 15 - Comprobante del pedido registrado (POST /api/pedidos).
 * El total y los subtotales los calcula SIEMPRE el backend.
 */
data class PedidoResponse(
    val idPedido: Long?,
    val clienteId: Long?,
    val fecha: String?,
    val total: BigDecimal?,
    val detalles: List<DetallePedidoResponse>?
)

/**
 * Línea del comprobante: precioUnitario congelado al momento de la compra.
 */
data class DetallePedidoResponse(
    val idProducto: Long?,
    val nombreProducto: String?,
    val cantidad: Int?,
    val precioUnitario: BigDecimal?,
    val subtotal: BigDecimal?
)
