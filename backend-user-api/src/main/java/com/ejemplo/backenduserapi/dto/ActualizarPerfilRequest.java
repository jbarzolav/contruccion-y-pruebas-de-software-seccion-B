package com.ejemplo.backenduserapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HU 28 - Cuerpo del PATCH /api/usuarios/me.
 *
 * Solo contiene campos personales editables: no existe idUsuario, rol,
 * permisos ni contraseña, por lo que el mass assignment es imposible y la
 * identidad la determina siempre la sesión Bearer.
 */
public class ActualizarPerfilRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato válido")
    @Size(max = 255, message = "El correo no puede superar 255 caracteres")
    private String correo;

    /** Opcional: si viaja vacío se limpia el teléfono guardado. */
    @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
    private String telefono;

    public ActualizarPerfilRequest() {
    }

    public ActualizarPerfilRequest(String nombre, String correo, String telefono) {
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }
}
