package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

public record ResenaResponse(Long id, Long idPedido, String destino, Long productoId,
                             Long vendedorId, String texto, LocalDateTime fecha) {
}
