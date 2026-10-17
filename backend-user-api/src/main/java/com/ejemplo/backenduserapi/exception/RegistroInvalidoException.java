package com.ejemplo.backenduserapi.exception;

/** HU 26 - Datos de registro inválidos (contraseñas o límite BCrypt) -> 400. */
public class RegistroInvalidoException extends RuntimeException {
    public RegistroInvalidoException(String mensaje) { super(mensaje); }
}
