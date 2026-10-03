package com.ejemplo.backenduserapi.exception;

/**
 * Se lanza cuando la imagen está vacía o su formato no está permitido.
 * Se traduce a HTTP 400 Bad Request.
 */
public class ImagenInvalidaException extends RuntimeException {

    public ImagenInvalidaException(String mensaje) {
        super(mensaje);
    }
}
