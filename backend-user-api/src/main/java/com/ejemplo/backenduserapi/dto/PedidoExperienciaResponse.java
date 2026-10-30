package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Detalle de pedido propio con la experiencia ya guardada. */
public record PedidoExperienciaResponse(Long idPedido, Long clienteId, LocalDateTime fecha,
                                        BigDecimal total, List<DetallePedidoResponse> detalles,
                                        CalificacionResponse calificacion,
                                        List<ResenaResponse> resenas) {
}
