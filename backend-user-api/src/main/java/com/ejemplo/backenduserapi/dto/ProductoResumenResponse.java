package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;

/**
 * Resumen de producto para el chatbot (HU 16).
 * Suficiente para pintar la tarjeta sugerida en web/móvil
 * y navegar a su detalle.
 */
public class ProductoResumenResponse {

    private final Long id;
    private final String nombre;
    private final BigDecimal precio;
    private final Integer stock;
    private final String categoria;
    private final String imagenUrl;

    public ProductoResumenResponse(Long id, String nombre, BigDecimal precio,
                                   Integer stock, String categoria, String imagenUrl) {
        this.id = id;
        this.nombre = nombre;
        this.precio = precio;
        this.stock = stock;
        this.categoria = categoria;
        this.imagenUrl = imagenUrl;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public Integer getStock() {
        return stock;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }
}
