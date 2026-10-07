package com.ejemplo.publicarproducto.model

import java.math.BigDecimal

/**
 * HU 11 / HU 12 - Ítem del carrito.
 * El subtotal (cantidad * precioUnitario) lo calcula siempre el backend.
 */
data class ItemCarritoResponse(
    val id: Long?,
    val productoId: Long?,
    val nombreProducto: String?,
    val cantidad: Int?,
    val precioUnitario: BigDecimal?,
    val subtotal: BigDecimal?,
    val stockDisponible: Int?
)
