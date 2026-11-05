package com.ejemplo.publicarproducto.model

data class NotificacionResponse(
    val idNotificacion: Long?,
    val tipo: String?,
    val resumen: String?,
    val fecha: String?,
    val estado: String?,
    val idPedido: Long?,
    val enlace: String?
)
