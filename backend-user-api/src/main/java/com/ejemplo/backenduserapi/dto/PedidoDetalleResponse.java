package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HU 50 - Detalle de un pedido propio.
 *
 * Cabecera: id, cliente, fecha, estado y fecha de actualización del estado.
 * Detalle: una línea por producto con cantidad, precio unitario registrado
 * en la operación y subtotal; el total es el valor persistido del pedido.
 */
public record PedidoDetalleResponse(Long idPedido, Long clienteId, LocalDateTime fecha,
                                    String estado, LocalDateTime fechaActualizacion,
                                    BigDecimal total, List<DetallePedidoResponse> detalles) {
}
