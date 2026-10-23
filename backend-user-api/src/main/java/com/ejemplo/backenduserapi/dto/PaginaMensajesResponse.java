package com.ejemplo.backenduserapi.dto;

import com.ejemplo.backenduserapi.dto.ConversacionResponse.MensajeResponse;
import java.util.List;

/** HU38 - Página de mensajes ordenados de una conversación. */
public record PaginaMensajesResponse(List<MensajeResponse> mensajes, int pagina, int tamano,
        long totalElementos, int totalPaginas, boolean tieneSiguiente) { }
