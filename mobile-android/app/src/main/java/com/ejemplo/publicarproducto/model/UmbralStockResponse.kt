package com.ejemplo.publicarproducto.model

/**
 * HU 58 - Umbral de stock de una publicación propia del vendedor.
 *
 * - `umbral` llega en null cuando el vendedor todavía no configuró umbral
 *   para esa publicación (no es un error).
 * - `stockBajo` indica que el stock actual ya está en o por debajo del umbral.
 * - `alertaNotificada` indica que el episodio de stock bajo actual ya generó
 *   un aviso: mientras siga activo no se repiten avisos.
 */
data class UmbralStockResponse(
    val idProducto: Long? = null,
    val nombre: String? = null,
    val imagenUrl: String? = null,
    val stock: Int? = null,
    val umbral: Int? = null,
    val stockBajo: Boolean? = null,
    val alertaNotificada: Boolean? = null,
    val fechaActualizacion: String? = null
)

/** Cuerpo de PUT /api/productos/{idProducto}/umbral-stock. */
data class UmbralStockRequest(val umbral: Int)

/**
 * HU 58 - Aviso de stock bajo del vendedor.
 *
 * El aviso nace en el sistema de notificaciones existente (tipo STOCK_BAJO);
 * esta vista lo enriquece con el stock actual y el umbral vigente.
 */
data class AvisoStockBajoResponse(
    val idNotificacion: Long? = null,
    val idProducto: Long? = null,
    val nombre: String? = null,
    val imagenUrl: String? = null,
    val stockActual: Int? = null,
    val umbral: Int? = null,
    val stockBajo: Boolean? = null,
    val estado: String? = null,
    val fecha: String? = null
)
