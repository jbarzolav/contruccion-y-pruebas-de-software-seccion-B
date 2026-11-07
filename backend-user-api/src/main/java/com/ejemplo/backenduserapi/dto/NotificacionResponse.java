package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

/**
 * HU53 - Notificación de la bandeja unificada.
 *
 * Los campos `idPedido` y `idConversacion` identifican el destino del enlace según
 * el tipo; `productoId` identifica la publicación en los avisos de stock bajo
 * (HU58); `fechaLectura` queda nulo mientras la notificación sigue pendiente.
 */
public record NotificacionResponse(
        Long idNotificacion,
        String tipo,
        String resumen,
        LocalDateTime fecha,
        String estado,
        Long idPedido,
        String enlace,
        Long idConversacion,
        LocalDateTime fechaLectura,
        Long productoId
) { }
