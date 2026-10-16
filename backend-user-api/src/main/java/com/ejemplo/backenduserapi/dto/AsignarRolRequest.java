package com.ejemplo.backenduserapi.dto;
import jakarta.validation.constraints.NotBlank;
/** HU 23 - Rol solicitado por un administrador. */
public class AsignarRolRequest {
    @NotBlank(message = "El rol es obligatorio")
    private String rol;
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}
