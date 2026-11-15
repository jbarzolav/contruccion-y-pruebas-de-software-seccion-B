package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuesta del POST /api/pedidos (HU 15 / HU 59).
 * Comprobante del pedido registrado: id, fecha, estado, total y el desglose
 * por producto. El estado nace siempre en "REGISTRADO" (HU 47/HU 49).
 */
public class PedidoResponse {

    private final Long idPedido;
    private final Long clienteId;
    private final LocalDateTime fecha;
    private final String estado;
    private final BigDecimal total;
    private final List<DetallePedidoResponse> detalles;

    public PedidoResponse(Long idPedido, Long clienteId, LocalDateTime fecha,
                          String estado, BigDecimal total, List<DetallePedidoResponse> detalles) {
        this.idPedido = idPedido;
        this.clienteId = clienteId;
        this.fecha = fecha;
        this.estado = estado;
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

    /** Estado vigente del pedido al momento de confirmar la compra. */
    public String getEstado() {
        return estado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<DetallePedidoResponse> getDetalles() {
        return detalles;
    }
}
