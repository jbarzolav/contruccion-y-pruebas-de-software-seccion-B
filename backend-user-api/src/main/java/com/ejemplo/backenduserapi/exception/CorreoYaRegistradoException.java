package com.ejemplo.backenduserapi.exception;

/** HU 26 - El correo ya pertenece a una cuenta -> 409. */
public class CorreoYaRegistradoException extends RuntimeException {
    public CorreoYaRegistradoException(String mensaje) { super(mensaje); }
}
