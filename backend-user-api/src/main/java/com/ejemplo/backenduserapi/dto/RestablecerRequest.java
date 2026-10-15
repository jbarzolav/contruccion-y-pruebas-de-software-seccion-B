package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.NotBlank;

public class RestablecerRequest {
    @NotBlank(message = "El token es obligatorio")
    private String token;
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    private String confirmacionPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmacionPassword() { return confirmacionPassword; }
    public void setConfirmacionPassword(String confirmacionPassword) {
        this.confirmacionPassword = confirmacionPassword;
    }
}
