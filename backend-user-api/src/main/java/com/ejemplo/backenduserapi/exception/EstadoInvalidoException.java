package com.ejemplo.backenduserapi.exception;

/**
 * Se lanza cuando el estado recibido no está permitido.
 * Se traduce a HTTP 400 Bad Request.
 */
public class EstadoInvalidoException extends RuntimeException {

    public EstadoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
