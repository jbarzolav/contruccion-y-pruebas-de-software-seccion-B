package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CodigoRecuperacionRequest {
    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9-]{6,40}$", message = "El código no tiene un formato válido")
    private String codigo;

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
}
