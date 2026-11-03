package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

/** HU 49 - Cambio de estado registrado en el historial del pedido. */
public record HistorialEstadoPedidoResponse(Long idPedido, String estadoAnterior, String estadoNuevo,
                                            LocalDateTime fechaCambio, Long usuarioId) {
}
