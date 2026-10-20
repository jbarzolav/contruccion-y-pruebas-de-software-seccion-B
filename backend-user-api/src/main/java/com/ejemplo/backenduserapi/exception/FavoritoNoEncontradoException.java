package com.ejemplo.backenduserapi.exception;

/** Se lanza cuando la relación favorito del usuario autenticado no existe. */
public class FavoritoNoEncontradoException extends RuntimeException {
    public FavoritoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
