package com.ejemplo.backenduserapi.exception;

/**
 * Se lanza cuando el producto solicitado no existe.
 * Se traduce a HTTP 404 Not Found.
 */
public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
