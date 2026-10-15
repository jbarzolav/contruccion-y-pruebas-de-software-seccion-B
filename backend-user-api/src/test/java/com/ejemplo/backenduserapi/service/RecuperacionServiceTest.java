package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.RestablecerRequest;
import com.ejemplo.backenduserapi.entity.TokenRecuperacion;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.RecuperacionInvalidaException;
import com.ejemplo.backenduserapi.repository.TokenRecuperacionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HU 22 - Pruebas unitarias de recuperación, vigencia y cambio de contraseña. */
@ExtendWith(MockitoExtension.class)
class RecuperacionServiceTest {
    @Mock private UsuarioRepository usuarios;
    @Mock private TokenRecuperacionRepository tokens;
    @Mock private CorreoRecuperacionService correo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private RecuperacionService servicio;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        servicio = new RecuperacionService(usuarios, tokens, encoder, correo, 15);
        usuario = new Usuario("hu22@pulgatec.com", encoder.encode("Anterior123*"));
        usuario.setId(22L);
    }

    private RestablecerRequest solicitud(String token, String confirmacion) {
        RestablecerRequest request = new RestablecerRequest();
        request.setToken(token);
        request.setPassword("Nueva123*");
        request.setConfirmacionPassword(confirmacion);
        return request;
    }

    @Test
    void solicitudValidaGeneraTokenTemporalYEnviaEnlace() {
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        Instant antes = Instant.now();
        servicio.solicitar(usuario.getCorreo());
        Instant despues = Instant.now();
        ArgumentCaptor<TokenRecuperacion> captor = ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(tokens).save(captor.capture());
        TokenRecuperacion token = captor.getValue();
        assertFalse(token.getToken().isBlank());
        assertSame(usuario, token.getUsuario());
        assertFalse(token.getVencimiento().isBefore(antes.plusSeconds(900)));
        assertFalse(token.getVencimiento().isAfter(despues.plusSeconds(900)));
        verify(correo).enviar(usuario.getCorreo(), token.getToken());
    }

    @Test
    void correoNoRegistradoNoGeneraTokenNiEnviaEnlace() {
        when(usuarios.findByCorreoIgnoreCase("nadie@pulgatec.com")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> servicio.solicitar("nadie@pulgatec.com"));
        verifyNoInteractions(tokens, correo);
    }

    @Test
    void cambioValidoGuardaBcryptEInvalidaTokensDelUsuario() {
        when(tokens.buscarParaConsumir("vigente")).thenReturn(Optional.of(
                new TokenRecuperacion("vigente", usuario, Instant.now().plusSeconds(60))));
        servicio.restablecer(solicitud("vigente", "Nueva123*"));
        assertNotEquals("Nueva123*", usuario.getPasswordHash());
        assertTrue(encoder.matches("Nueva123*", usuario.getPasswordHash()));
        assertFalse(encoder.matches("Anterior123*", usuario.getPasswordHash()));
        verify(usuarios).save(usuario);
        verify(tokens).deleteByUsuarioId(22L);
    }

    @Test
    void tokenInvalidoNoModificaPassword() {
        when(tokens.buscarParaConsumir("invalido")).thenReturn(Optional.empty());
        String hashAnterior = usuario.getPasswordHash();
        assertThrows(RecuperacionInvalidaException.class,
                () -> servicio.restablecer(solicitud("invalido", "Nueva123*")));
        assertEquals(hashAnterior, usuario.getPasswordHash());
        verify(usuarios, never()).save(any());
        verify(tokens, never()).deleteByUsuarioId(anyLong());
    }

    @Test
    void tokenVencidoNoModificaPassword() {
        when(tokens.buscarParaConsumir("vencido")).thenReturn(Optional.of(
                new TokenRecuperacion("vencido", usuario, Instant.now().minusSeconds(60))));
        String hashAnterior = usuario.getPasswordHash();
        assertThrows(RecuperacionInvalidaException.class,
                () -> servicio.restablecer(solicitud("vencido", "Nueva123*")));
        assertEquals(hashAnterior, usuario.getPasswordHash());
        verify(usuarios, never()).save(any());
        verify(tokens, never()).deleteByUsuarioId(anyLong());
    }

    @Test
    void contrasenasDistintasNoConsumenToken() {
        assertThrows(RecuperacionInvalidaException.class,
                () -> servicio.restablecer(solicitud("vigente", "Distinta123*")));
        verifyNoInteractions(tokens);
        verify(usuarios, never()).save(any());
    }

    @Test
    void passwordQueSuperaLimiteBcryptNoConsumeToken() {
        RestablecerRequest request = solicitud("vigente", "á".repeat(40));
        request.setPassword("á".repeat(40));
        assertThrows(RecuperacionInvalidaException.class, () -> servicio.restablecer(request));
        verifyNoInteractions(tokens);
        verify(usuarios, never()).save(any());
    }
}
