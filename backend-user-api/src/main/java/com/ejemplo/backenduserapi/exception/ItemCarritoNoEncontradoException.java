package com.ejemplo.backenduserapi.exception;

/**
 * Ítem del carrito inexistente (HU 12) -> 404.
 */
public class ItemCarritoNoEncontradoException extends RuntimeException {

    public ItemCarritoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
