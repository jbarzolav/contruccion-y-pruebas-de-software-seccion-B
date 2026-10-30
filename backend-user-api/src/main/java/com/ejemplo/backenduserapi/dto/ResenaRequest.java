package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HU 42 - Autor y pedido se derivan de sesión y path. */
public class ResenaRequest {
    @NotNull(message = "El destino es obligatorio")
    private String destino;

    @NotBlank(message = "El texto es obligatorio")
    @Size(max = 1000, message = "La reseña no puede superar 1000 caracteres")
    private String texto;

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
}
