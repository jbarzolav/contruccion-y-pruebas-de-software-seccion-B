package com.ejemplo.publicarproducto.model

/** HU 44 - Campos públicos de una reseña asociada a un producto. */
data class ResenaProductoPublicaResponse(
    val id: Long? = null,
    val productoId: Long? = null,
    val nombreAutor: String = "Usuario",
    val calificacion: Int? = null,
    val texto: String = "",
    val fecha: String = ""
)
