package com.ejemplo.backenduserapi.dto;

import com.ejemplo.backenduserapi.entity.Mensaje;
import java.time.Instant;
import java.util.List;

public record ConversacionResponse(Long idConversacion, Long productoId, String productoNombre,
        String productoImagenUrl, boolean productoDisponible, Instant fechaCreacion, List<MensajeResponse> mensajes) {
    public record MensajeResponse(Long id, Long emisorId, String emisorRol, String contenido, Instant fecha) {
        public static MensajeResponse desde(Mensaje mensaje) {
            return new MensajeResponse(mensaje.getId(), mensaje.getEmisor().getId(),
                    mensaje.getEmisor().getRol().name(), mensaje.getContenido(), mensaje.getFecha());
        }
    }
}
