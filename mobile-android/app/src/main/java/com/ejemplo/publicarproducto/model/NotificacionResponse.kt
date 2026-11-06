package com.ejemplo.publicarproducto.model

data class NotificacionResponse(
    val idNotificacion: Long?,
    val tipo: String?,
    val resumen: String?,
    val fecha: String?,
    val estado: String?,
    val idPedido: Long?,
    val enlace: String?,
    // HU 53 - Destino del aviso según el tipo; nulo en los avisos de pedido.
    val idConversacion: Long? = null,
    // HU 54 - Fecha en que se marcó como leída; nulo mientras sigue pendiente.
    val fechaLectura: String? = null
)
