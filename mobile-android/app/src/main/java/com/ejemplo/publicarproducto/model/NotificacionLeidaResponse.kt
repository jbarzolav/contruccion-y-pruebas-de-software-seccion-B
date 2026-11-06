package com.ejemplo.publicarproducto.model

/** HU 54 - Resultado de marcar una notificación propia como leída (idempotente). */
data class NotificacionLeidaResponse(
    val idNotificacion: Long,
    val estado: String?,
    val fechaLectura: String?,
    val pendientes: Long
)
