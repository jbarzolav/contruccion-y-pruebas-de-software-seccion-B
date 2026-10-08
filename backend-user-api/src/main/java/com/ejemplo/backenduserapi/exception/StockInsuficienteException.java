package com.ejemplo.backenduserapi.exception;

/**
 * El carrito pide más unidades de las que hay en la BD (HU 15) -> 400.
 * El pedido NO se registra y el stock queda intacto.
 */
public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
