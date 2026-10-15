package com.ejemplo.backenduserapi.exception;

/**
 * Se lanza cuando el login recibe un correo inexistente o una
 * contraseña incorrecta. Siempre se lanza con el mismo mensaje para
 * no revelar si la cuenta existe.
 * Se traduce a HTTP 401 Unauthorized.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
