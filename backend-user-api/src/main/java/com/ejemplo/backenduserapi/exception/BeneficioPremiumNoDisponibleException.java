package com.ejemplo.backenduserapi.exception;

/**
 * HU 19 - Se lanza cuando un vendedor sin plan Premium intenta
 * destacar una publicación. Se traduce a HTTP 403 Forbidden.
 */
public class BeneficioPremiumNoDisponibleException extends RuntimeException {

    public BeneficioPremiumNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
