package com.ejemplo.backenduserapi.exception;
import org.springframework.http.HttpStatus;
/** HU 23 - Errores de sesión, permisos y asignación de roles. */
public class AutorizacionException extends RuntimeException {
    private final HttpStatus estado;
    public AutorizacionException(HttpStatus estado, String mensaje) { super(mensaje); this.estado = estado; }
    public HttpStatus getEstado() { return estado; }
}
