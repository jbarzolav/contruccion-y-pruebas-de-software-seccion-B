package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * HU 12 - Cuerpo de PUT /api/carrito/items/{idItemCarrito}
 * { "cantidad": 3 }
 */
public class ItemCarritoCantidadRequest {

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer cantidad;

    public ItemCarritoCantidadRequest() {
    }

    public ItemCarritoCantidadRequest(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }
}
