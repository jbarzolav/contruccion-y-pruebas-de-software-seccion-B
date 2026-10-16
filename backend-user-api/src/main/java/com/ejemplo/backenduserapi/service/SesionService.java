package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.AuthMessageResponse;
import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cierre de la sesión opaca actual; reutiliza su mecanismo de validación. */
@Service
public class SesionService {
    private static final String PREFIJO_BEARER = "Bearer ";
    private final AutorizacionService autorizacion;
    private final TokenService tokens;
    private final AuditService auditoria;

    public SesionService(AutorizacionService autorizacion, TokenService tokens, AuditService auditoria) {
        this.autorizacion = autorizacion;
        this.tokens = tokens;
        this.auditoria = auditoria;
    }

    @Transactional
    public AuthMessageResponse cerrar(String cabeceraAutorizacion) {
        if (cabeceraAutorizacion == null || !cabeceraAutorizacion.startsWith(PREFIJO_BEARER)
                || cabeceraAutorizacion.substring(PREFIJO_BEARER.length()).isBlank()) {
            throw new AutorizacionException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión.");
        }
        Usuario usuario = autorizacion.autenticar(cabeceraAutorizacion);
        auditoria.registrar(usuario, AccionAuditoria.LOGOUT);
        tokens.invalidar(cabeceraAutorizacion.substring(PREFIJO_BEARER.length()));
        return new AuthMessageResponse("Sesión cerrada correctamente.");
    }
}
