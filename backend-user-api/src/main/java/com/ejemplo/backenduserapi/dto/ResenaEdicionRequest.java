package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU 45 - Solo permite modificar el texto de una reseña propia. */
public class ResenaEdicionRequest {
    @NotBlank(message = "El texto es obligatorio")
    @Size(max = 1000, message = "La reseña no puede superar 1000 caracteres")
    private String texto;

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
}
