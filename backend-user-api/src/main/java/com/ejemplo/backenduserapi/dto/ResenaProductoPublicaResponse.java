package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

/** HU 44 - Campos públicos de una reseña asociada a un producto. */
public record ResenaProductoPublicaResponse(
        Long id,
        Long productoId,
        String nombreAutor,
        Integer calificacion,
        String texto,
        LocalDateTime fecha
) {
}
