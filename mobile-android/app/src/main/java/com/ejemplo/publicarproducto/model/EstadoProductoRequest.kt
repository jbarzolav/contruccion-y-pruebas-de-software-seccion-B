package com.ejemplo.publicarproducto.model

/**
 * Cuerpo del endpoint HU 04:
 * PATCH /api/productos/{idProducto}/estado
 *
 * Enviar { "estado": "RETIRADO" } para aplicar la baja lógica
 * (el producto no se elimina de la base de datos).
 */
data class EstadoProductoRequest(
    val estado: String
)
