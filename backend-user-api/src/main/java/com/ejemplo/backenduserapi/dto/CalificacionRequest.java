package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** HU 41 - La identidad y el vendedor se derivan de sesión/pedido. */
public class CalificacionRequest {
    @NotNull(message = "El valor es obligatorio")
    @Min(value = 1, message = "La calificación mínima es 1")
    @Max(value = 5, message = "La calificación máxima es 5")
    private Integer valor;

    public Integer getValor() { return valor; }
    public void setValor(Integer valor) { this.valor = valor; }
}
