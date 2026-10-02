package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ImagenProductoRequest {

    @NotBlank(message = "La imagen es obligatoria")
    private String imagenUrl;

    @NotNull(message = "El vendedor es obligatorio")
    private Long vendedorId;

    public ImagenProductoRequest() {
    }

    public ImagenProductoRequest(String imagenUrl, Long vendedorId) {
        this.imagenUrl = imagenUrl;
        this.vendedorId = vendedorId;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public Long getVendedorId() {
        return vendedorId;
    }

    public void setVendedorId(Long vendedorId) {
        this.vendedorId = vendedorId;
    }
}