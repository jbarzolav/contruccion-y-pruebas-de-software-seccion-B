package com.ejemplo.backenduserapi.dto;

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
