package com.ejemplo.publicarproducto.model

/**
 * HU 10 - Respuesta de disponibilidad de un producto.
 */
data class DisponibilidadProductoResponse(
    val idProducto: Long,
    val stock: Int,
    val estado: String,
    val disponible: Boolean
)
