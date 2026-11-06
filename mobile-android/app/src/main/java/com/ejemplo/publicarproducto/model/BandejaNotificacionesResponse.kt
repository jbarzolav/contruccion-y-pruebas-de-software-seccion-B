package com.ejemplo.publicarproducto.model

/**
 * HU 53 - Página de la bandeja unificada de notificaciones.
 *
 * `pendientes` es el total de avisos sin leer del usuario en toda su bandeja
 * (no solo los de esta página).
 */
data class BandejaNotificacionesResponse(
    val notificaciones: List<NotificacionResponse>,
    val pagina: Int,
    val tamano: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val tieneSiguiente: Boolean,
    val pendientes: Long
)
