package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SesionServiceTest {
    @Mock private AutorizacionService autorizacion;
    @Mock private TokenService tokens;
    @Mock private AuditService auditoria;
    private SesionService servicio;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        servicio = new SesionService(autorizacion, tokens, auditoria);
        usuario = new Usuario("cuenta@prueba.local", "hash");
        usuario.setRol(Rol.CLIENTE);
    }

    @Test
    void logoutValidoAuditaEInvalidaLaSesion() {
        when(autorizacion.autenticar("Bearer opaco-valido")).thenReturn(usuario);
        assertEquals("Sesión cerrada correctamente.", servicio.cerrar("Bearer opaco-valido").mensaje());
        verify(auditoria).registrar(usuario, AccionAuditoria.LOGOUT);
        verify(tokens).invalidar("opaco-valido");
    }

    @Test
    void logoutSinBearerDevuelve401SinTocarSesion() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(AutorizacionException.class,
                () -> servicio.cerrar(null)).getEstado());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(AutorizacionException.class,
                () -> servicio.cerrar("Bearer ")).getEstado());
        verifyNoInteractions(autorizacion, tokens, auditoria);
    }

    @Test
    void tokenInvalidoNoGeneraEventoNiSeInvalidaOtraSesion() {
        when(autorizacion.autenticar("Bearer inventado"))
                .thenThrow(new AutorizacionException(HttpStatus.UNAUTHORIZED, "Sesión inválida."));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(AutorizacionException.class,
                () -> servicio.cerrar("Bearer inventado")).getEstado());
        verifyNoInteractions(auditoria);
        verify(tokens, never()).invalidar(anyString());
    }
}
