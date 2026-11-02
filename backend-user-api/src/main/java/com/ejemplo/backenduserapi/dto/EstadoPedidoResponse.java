package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

/** HU 47 - Estado actual de un pedido propio. */
public record EstadoPedidoResponse(Long idPedido, String estado, LocalDateTime fechaActualizacion) {
}
