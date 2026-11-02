package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** HU 48 - Pedido recibido con solo las líneas del vendedor autenticado. */
public record PedidoVendedorResponse(Long idPedido, LocalDateTime fecha, String estado,
                                    LocalDateTime fechaActualizacion, BigDecimal total,
                                    List<DetallePedidoResponse> productos) {
}
