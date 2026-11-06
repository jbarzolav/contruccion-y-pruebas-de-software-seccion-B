package com.ejemplo.backenduserapi.dto;

/** HU54 - Confirmación de la lectura masiva, con el contador de pendientes actualizado. */
public record NotificacionesLeidasResponse(
        int marcadas,
        long pendientes
) { }
