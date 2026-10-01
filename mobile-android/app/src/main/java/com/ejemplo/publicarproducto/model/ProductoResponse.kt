package com.ejemplo.publicarproducto.model

import java.math.BigDecimal

/**
 * Respuesta del backend al registrar un producto.
 */
data class ProductoResponse(
    val id: Long?,
    val nombre: String?,
    val descripcion: String?,
    val precio: BigDecimal?,
    val stock: Int?,
    val categoria: String?,
    val estado: String?,
    val imagenUrl: String?,
    val vendedorId: Long?
)

/**
 * Cuerpo de error del backend cuando la validación falla (HTTP 400).
 * { "status": 400, "error": "Validación fallida", "messages": ["..."] }
 */
data class ErrorResponse(
    val status: Int? = null,
    val error: String? = null,
    val messages: List<String>? = null
)
