package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * HU 58 - Cuerpo de PUT /api/productos/{idProducto}/umbral-stock.
 *
 * El umbral es un numero entero mayor o igual a cero. El valor null o los
 * negativos se rechazan con 400 (validacion de Jakarta).
 */
public class UmbralStockRequest {

    @NotNull(message = "El umbral es obligatorio")
    @Min(value = 0, message = "El umbral no puede ser negativo")
    private Integer umbral;

    public UmbralStockRequest() {
    }

    public UmbralStockRequest(Integer umbral) {
        this.umbral = umbral;
    }

    public Integer getUmbral() {
        return umbral;
    }

    public void setUmbral(Integer umbral) {
        this.umbral = umbral;
    }
}
