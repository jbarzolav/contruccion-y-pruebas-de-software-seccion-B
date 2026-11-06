package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

/** HU54 - Confirmación de la lectura individual, con el contador de pendientes actualizado. */
public record NotificacionLeidaResponse(
        Long idNotificacion,
        String estado,
        LocalDateTime fechaLectura,
        long pendientes
) { }
