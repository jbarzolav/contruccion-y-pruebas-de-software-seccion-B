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
    val detalles: List<DetallePedidoResponse>?,
    val calificacion: CalificacionResponse? = null,
    val resenas: List<ResenaResponse>? = null
)

data class CalificacionResponse(val id: Long?, val idPedido: Long?, val valor: Int?, val fecha: String?)
data class ResenaResponse(val id: Long?, val idPedido: Long?, val destino: String?, val productoId: Long?,
                          val vendedorId: Long?, val texto: String?, val fecha: String?,
                          val fechaModificacion: String? = null)
data class CalificacionRequest(val valor: Int)
data class ResenaRequest(val destino: String, val texto: String)
data class ResenaEdicionRequest(val texto: String)

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
