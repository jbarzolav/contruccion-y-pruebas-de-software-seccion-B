package com.ejemplo.publicarproducto.model

/**
 * HU 19 - Cuerpo de PATCH /api/productos/{idProducto}/destacado.
 * destacado = true  -> activar el destacado
 * destacado = false -> desactivar el destacado
 */
data class DestacarRequest(
    val destacado: Boolean? = null
)
