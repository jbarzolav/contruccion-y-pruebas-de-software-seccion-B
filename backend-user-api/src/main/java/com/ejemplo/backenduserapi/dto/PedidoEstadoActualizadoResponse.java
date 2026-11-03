package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HU 49 - Resultado de PATCH /api/pedidos/{idPedido}/estado.
 *
 * Devuelve el estado vigente, el estado anterior, la fecha del cambio,
 * los estados que admite el pedido a continuación y el historial registrado.
 */
public record PedidoEstadoActualizadoResponse(Long idPedido, String estadoAnterior, String estado,
                                              LocalDateTime fechaActualizacion,
                                              List<String> estadosPermitidos,
                                              List<HistorialEstadoPedidoResponse> historial) {
}
