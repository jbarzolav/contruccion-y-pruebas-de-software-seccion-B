package com.ejemplo.publicarproducto.model

/** Ventas del usuario con los estados que aún no confirmó el servidor. */
data class VentasLocales(
    val pedidos: List<PedidoVendedorResponse>,
    val estadosPendientes: Map<Long, String>
)
