package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

public record NotificacionResponse(
        Long idNotificacion,
        String tipo,
        String resumen,
        LocalDateTime fecha,
        String estado,
        Long idPedido,
        String enlace
) { }
