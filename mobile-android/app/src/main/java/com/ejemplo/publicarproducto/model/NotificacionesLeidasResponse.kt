package com.ejemplo.publicarproducto.model

/** HU 54 - Resultado de marcar todas las notificaciones pendientes como leídas. */
data class NotificacionesLeidasResponse(
    val marcadas: Int,
    val pendientes: Long
)
