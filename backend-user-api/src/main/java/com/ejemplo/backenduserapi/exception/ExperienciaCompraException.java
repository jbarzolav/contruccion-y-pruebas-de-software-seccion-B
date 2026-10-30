package com.ejemplo.backenduserapi.exception;

import org.springframework.http.HttpStatus;

public class ExperienciaCompraException extends RuntimeException {
    private final HttpStatus estado;

    public ExperienciaCompraException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() { return estado; }
}
