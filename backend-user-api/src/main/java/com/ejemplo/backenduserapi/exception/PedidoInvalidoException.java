package com.ejemplo.backenduserapi.exception;

/**
 * Regla de negocio del pedido violada (HU 15) -> 400.
 * Ej.: carrito vacío o clienteId inválido.
 */
public class PedidoInvalidoException extends RuntimeException {

    public PedidoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
