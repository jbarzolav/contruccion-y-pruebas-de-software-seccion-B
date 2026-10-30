package com.ejemplo.backenduserapi.dto;

import com.ejemplo.backenduserapi.dto.ConversacionResponse.MensajeResponse;
import java.time.Instant;

/** HU38 - Resumen de una conversación para la lista del usuario. */
public record ConversacionResumenResponse(Long idConversacion, Long productoId, String productoNombre,
        String productoImagenUrl, boolean productoDisponible, Instant fechaCreacion, MensajeResponse ultimoMensaje) { }
