package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

public record CalificacionResponse(Long id, Long idPedido, Integer valor, LocalDateTime fecha) {
}
