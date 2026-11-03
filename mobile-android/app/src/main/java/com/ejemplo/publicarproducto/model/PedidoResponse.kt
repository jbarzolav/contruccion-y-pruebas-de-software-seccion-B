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
    val resenas: List<ResenaResponse>? = null,
    val estado: String? = null,
    val fechaActualizacion: String? = null
)

data class EstadoPedidoResponse(val idPedido: Long, val estado: String, val fechaActualizacion: String?)

/**
 * HU 49 - Cuerpo de PATCH /api/pedidos/{idPedido}/estado.
 * El backend valida que la transición esté permitida.
 */
data class EstadoPedidoRequest(val estado: String)

/** HU 49 - Cambio de estado registrado en el historial del pedido. */
data class HistorialEstadoPedidoResponse(
    val idPedido: Long?,
    val estadoAnterior: String?,
    val estadoNuevo: String?,
    val fechaCambio: String?,
    val usuarioId: Long?
)

/** HU 49 - Respuesta del backend al actualizar el estado de un pedido. */
data class PedidoEstadoActualizadoResponse(
    val idPedido: Long?,
    val estadoAnterior: String?,
    val estado: String?,
    val fechaActualizacion: String?,
    val estadosPermitidos: List<String>?,
    val historial: List<HistorialEstadoPedidoResponse>?
)

/**
 * HU 50 - Detalle del pedido propio: cabecera + líneas con el precio registrado
 * en la operación y el total persistido.
 */
data class PedidoDetalleResponse(
    val idPedido: Long?,
    val clienteId: Long?,
    val fecha: String?,
    val estado: String?,
    val fechaActualizacion: String?,
    val total: BigDecimal?,
    val detalles: List<DetallePedidoResponse>?
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
