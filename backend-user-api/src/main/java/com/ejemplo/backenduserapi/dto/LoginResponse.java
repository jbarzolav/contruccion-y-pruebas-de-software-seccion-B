package com.ejemplo.backenduserapi.dto;

import com.ejemplo.backenduserapi.entity.Rol;
import java.util.Set;
/**
 * HU 21 - Respuesta del login (200 OK).
 *
 * DTO plano con el token opaco de sesión y los datos mínimos de la
 * cuenta. Nunca expone password ni passwordHash.
 */
public class LoginResponse {

    /** Token opaco a enviar como "Authorization: Bearer {token}". */
    private String token;

    private Long id;

    private String correo;

    // HU 23 - Rol y permisos para las interfaces web y Android.
    private Rol rol = Rol.CLIENTE;
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public Set<String> getPermisos() { return rol.getPermisos(); }

    public LoginResponse() {
    }

    public LoginResponse(String token, Long id, String correo) {
        this.token = token;
        this.id = id;
        this.correo = correo;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }
}
