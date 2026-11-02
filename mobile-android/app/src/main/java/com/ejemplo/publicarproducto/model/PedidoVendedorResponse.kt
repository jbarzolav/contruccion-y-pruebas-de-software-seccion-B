package com.ejemplo.publicarproducto.model

import java.math.BigDecimal

data class PedidoVendedorResponse(
    val idPedido: Long,
    val fecha: String?,
    val estado: String,
    val fechaActualizacion: String?,
    val total: BigDecimal,
    val productos: List<DetallePedidoResponse>
)
