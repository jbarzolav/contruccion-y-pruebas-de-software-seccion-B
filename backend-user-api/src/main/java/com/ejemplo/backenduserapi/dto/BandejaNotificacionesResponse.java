package com.ejemplo.backenduserapi.dto;

import java.util.List;

/**
 * HU53 - Página de la bandeja unificada de notificaciones.
 *
 * `pendientes` es el total de notificaciones sin leer del usuario en toda su
 * bandeja (no solo las de esta página).
 */
public record BandejaNotificacionesResponse(
        List<NotificacionResponse> notificaciones,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        boolean tieneSiguiente,
        long pendientes
) { }
