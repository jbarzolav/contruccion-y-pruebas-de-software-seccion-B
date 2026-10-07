package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;

/**
 * Respuesta de un ítem del carrito (HU 11 / HU 12).
 * El subtotal lo calcula SIEMPRE el servidor: cantidad * precioUnitario.
 */
public class ItemCarritoResponse {

    private Long id;
    private Long productoId;
    private String nombreProducto;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    private Integer stockDisponible;

    public ItemCarritoResponse(Long id, Long productoId, String nombreProducto, Integer cantidad,
                               BigDecimal precioUnitario, BigDecimal subtotal, Integer stockDisponible) {
        this.id = id;
        this.productoId = productoId;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
        this.stockDisponible = stockDisponible;
    }

    public Long getId() {
        return id;
    }

    public Long getProductoId() {
        return productoId;
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

    public Integer getStockDisponible() {
        return stockDisponible;
    }
}
