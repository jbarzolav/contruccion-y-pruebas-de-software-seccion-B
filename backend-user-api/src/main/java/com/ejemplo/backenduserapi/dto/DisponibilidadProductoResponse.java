package com.ejemplo.backenduserapi.dto;

public class DisponibilidadProductoResponse {

    private Long idProducto;
    private Integer stock;
    private String estado;
    private boolean disponible;

    public DisponibilidadProductoResponse(Long idProducto, Integer stock, String estado, boolean disponible) {
        this.idProducto = idProducto;
        this.stock = stock;
        this.estado = estado;
        this.disponible = disponible;
    }

    public Long getIdProducto() {
        return idProducto;
    }

    public Integer getStock() {
        return stock;
    }

    public String getEstado() {
        return estado;
    }

    public boolean isDisponible() {
        return disponible;
    }
}
