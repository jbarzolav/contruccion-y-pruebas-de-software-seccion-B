package com.ejemplo.publicarproducto.model

import java.math.BigDecimal

/**
 * HU 48 - Pedido recibido con solo los productos del vendedor autenticado.
 * HU 49 - estadosPermitidos indica las transiciones válidas del pedido.
 */
data class PedidoVendedorResponse(
    val idPedido: Long,
    val fecha: String?,
    val estado: String,
    val fechaActualizacion: String?,
    val total: BigDecimal,
    val productos: List<DetallePedidoResponse>,
    val estadosPermitidos: List<String>? = null
)
