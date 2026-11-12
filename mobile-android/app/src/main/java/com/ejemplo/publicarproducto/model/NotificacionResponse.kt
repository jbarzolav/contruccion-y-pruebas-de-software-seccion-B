package com.ejemplo.publicarproducto.model

/**
 * Aviso del usuario en la bandeja unificada.
 *
 * - HU 51 / HU 52 - aviso ligado a una compra: el backend genera el aviso
 *   `NUEVA_COMPRA` para cada vendedor con productos en el pedido (HU 52) y el
 *   `CAMBIO_ESTADO_PEDIDO` para el cliente que compró (HU 51).
 * - HU 53 - bandeja unificada con filtros combinables.
 * - HU 54 - lectura individual o masiva de los avisos propios.
 * - HU 58 - aviso de stock bajo de una publicación del vendedor.
 */
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
    val fechaLectura: String? = null,
    // HU 58 - Publicación del aviso de stock bajo; nulo en los demás avisos.
    val productoId: Long? = null
)
