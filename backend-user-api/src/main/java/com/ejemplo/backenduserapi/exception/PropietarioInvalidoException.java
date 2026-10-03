package com.ejemplo.backenduserapi.exception;

/**
 * Se lanza cuando el producto pertenece a otro vendedor
 * (o cuando el vendedor no puede verificarse de forma segura).
 * Se traduce a HTTP 403 Forbidden.
 */
public class PropietarioInvalidoException extends RuntimeException {

    public PropietarioInvalidoException(String mensaje) {
        super(mensaje);
    }
}
