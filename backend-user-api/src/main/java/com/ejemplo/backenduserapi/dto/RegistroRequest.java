package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HU 26 - Cuerpo del POST /api/auth/registro.
 *
 * El rol nunca viaja en la petición: el backend asigna CLIENTE.
 */
public class RegistroRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato válido")
    private String correo;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    private String confirmacionPassword;

    public RegistroRequest() {
    }

    public RegistroRequest(String correo, String password, String confirmacionPassword) {
        this.correo = correo;
        this.password = password;
        this.confirmacionPassword = confirmacionPassword;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmacionPassword() {
        return confirmacionPassword;
    }

    public void setConfirmacionPassword(String confirmacionPassword) {
        this.confirmacionPassword = confirmacionPassword;
    }
}
