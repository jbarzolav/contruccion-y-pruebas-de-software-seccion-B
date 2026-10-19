package com.ejemplo.backenduserapi.exception;

/** HU 28 / HU 29 - Campos de perfil inválidos -> 400. */
public class PerfilInvalidoException extends RuntimeException {

    public PerfilInvalidoException(String mensaje) {
        super(mensaje);
    }
}
