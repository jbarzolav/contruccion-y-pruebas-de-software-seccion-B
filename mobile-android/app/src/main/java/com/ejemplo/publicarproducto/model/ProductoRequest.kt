package com.ejemplo.publicarproducto.model

/**
 * DTO que envía la app al backend: POST /api/productos
 */
data class ProductoRequest(
    val nombre: String,
    val descripcion: String,
    val precio: Double,
    val stock: Int,
    val categoria: String,
    val estado: String,
    val imagenUrl: String,
    val vendedorId: Long
)
