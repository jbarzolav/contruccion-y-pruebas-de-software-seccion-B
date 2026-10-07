package com.ejemplo.backenduserapi.exception;

/**
 * Regla de negocio del carrito violada (HU 11 / HU 12) -> 400.
 * Ej.: cantidad superior al stock disponible.
 */
public class ItemCarritoInvalidoException extends RuntimeException {

    public ItemCarritoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
