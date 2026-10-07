package com.ejemplo.publicarproducto.model

/**
 * HU 12 - Cuerpo de PUT /api/carrito/items/{idItemCarrito}
 * { "cantidad": 3 }
 */
data class ItemCarritoCantidadRequest(
    val cantidad: Int
)
