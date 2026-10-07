package com.ejemplo.publicarproducto.model

/**
 * HU 11 - Cuerpo de POST /api/carrito/items
 * { "productoId": 1, "cantidad": 2 }
 */
data class ItemCarritoRequest(
    val productoId: Long,
    val cantidad: Int
)
