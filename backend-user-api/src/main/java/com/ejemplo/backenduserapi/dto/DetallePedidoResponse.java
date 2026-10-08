package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;

/**
 * Línea del comprobante de pedido (HU 15).
 * El subtotal lo calcula el servidor (cantidad * precioUnitario congelado).
 */
public class DetallePedidoResponse {

    private final Long idProducto;
    private final String nombreProducto;
    private final Integer cantidad;
    private final BigDecimal precioUnitario;
    private final BigDecimal subtotal;

    public DetallePedidoResponse(Long idProducto, String nombreProducto, Integer cantidad,
                                 BigDecimal precioUnitario, BigDecimal subtotal) {
        this.idProducto = idProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
    }

    public Long getIdProducto() {
        return idProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
