package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HU 29 - Cuerpo del PATCH /api/vendedores/me/perfil.
 *
 * No incluye usuarioId ni id: el propietario lo determina la sesión Bearer.
 */
public class PerfilVendedorRequest {

    @NotBlank(message = "El nombre comercial es obligatorio")
    @Size(max = 80, message = "El nombre comercial no puede superar 80 caracteres")
    private String nombreComercial;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
    private String descripcion;

    @NotBlank(message = "El teléfono de contacto es obligatorio")
    @Size(max = 20, message = "El teléfono de contacto no puede superar 20 caracteres")
    private String telefonoContacto;

    public PerfilVendedorRequest() {
    }

    public PerfilVendedorRequest(String nombreComercial, String descripcion, String telefonoContacto) {
        this.nombreComercial = nombreComercial;
        this.descripcion = descripcion;
        this.telefonoContacto = telefonoContacto;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public void setTelefonoContacto(String telefonoContacto) {
        this.telefonoContacto = telefonoContacto;
    }
}
