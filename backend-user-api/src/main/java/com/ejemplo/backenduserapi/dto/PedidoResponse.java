package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuesta del POST /api/pedidos (HU 15).
 * Comprobante del pedido registrado: id, fecha, total y el desglose por producto.
 */
public class PedidoResponse {

    private final Long idPedido;
    private final Long clienteId;
    private final LocalDateTime fecha;
    private final BigDecimal total;
    private final List<DetallePedidoResponse> detalles;

    public PedidoResponse(Long idPedido, Long clienteId, LocalDateTime fecha,
                          BigDecimal total, List<DetallePedidoResponse> detalles) {
        this.idPedido = idPedido;
        this.clienteId = clienteId;
        this.fecha = fecha;
        this.total = total;
        this.detalles = detalles;
    }

    public Long getIdPedido() {
        return idPedido;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<DetallePedidoResponse> getDetalles() {
        return detalles;
    }
}
