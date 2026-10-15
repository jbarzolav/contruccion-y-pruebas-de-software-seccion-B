package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.LoginRequest;
import com.ejemplo.backenduserapi.dto.LoginResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.CredencialesInvalidasException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * HU 21 - Prueba unitaria pura (sin levantar Spring) del servicio de
 * autenticación. Se usa BCryptPasswordEncoder REAL para verificar el
 * hash de verdad; solo el repository se mockea.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void configurar() {
        authService = new AuthService(usuarioRepository, passwordEncoder, new TokenService());
    }

    // ============================================================
    // AUXILIARES
    // ============================================================
    private LoginRequest solicitud(String correo, String password) {
        return new LoginRequest(correo, password);
    }

    private Usuario usuarioDemo() {
        Usuario usuario =
                new Usuario("demo@pulgatec.com", passwordEncoder.encode("Demo123*"));
        usuario.setId(1L);
        return usuario;
    }

    // ============================================================
    // LOGIN CORRECTO
    // ============================================================
    @Test
    void debeIniciarSesionConCredencialesCorrectas() {
        Usuario demo = usuarioDemo();
        when(usuarioRepository.findByCorreoIgnoreCase("demo@pulgatec.com"))
                .thenReturn(Optional.of(demo));

        LoginResponse respuesta =
                authService.login(solicitud("demo@pulgatec.com", "Demo123*"));

        assertEquals(1L, respuesta.getId());
        assertEquals("demo@pulgatec.com", respuesta.getCorreo());
    }

    // ============================================================
    // CONTRASEÑA INCORRECTA / USUARIO INEXISTENTE -> mismo error
    // ============================================================
    @Test
    void debeLanzarCredencialesInvalidasConPasswordIncorrecta() {
        Usuario demo = usuarioDemo();
        when(usuarioRepository.findByCorreoIgnoreCase("demo@pulgatec.com"))
                .thenReturn(Optional.of(demo));

        CredencialesInvalidasException excepcion = assertThrows(
                CredencialesInvalidasException.class,
                () -> authService.login(solicitud("demo@pulgatec.com", "OtraClave1*")));

        assertEquals(AuthService.MENSAJE_CREDENCIALES_INVALIDAS, excepcion.getMessage());
    }

    @Test
    void debeLanzarElMismoErrorSiElUsuarioNoExiste() {
        when(usuarioRepository.findByCorreoIgnoreCase("nadie@correo.com"))
                .thenReturn(Optional.empty());

        CredencialesInvalidasException excepcion = assertThrows(
                CredencialesInvalidasException.class,
                () -> authService.login(solicitud("nadie@correo.com", "CualquierClave1*")));

        // Mismo mensaje que el caso de contraseña incorrecta: no se
        // revela si la cuenta existe o no
        assertEquals(AuthService.MENSAJE_CREDENCIALES_INVALIDAS, excepcion.getMessage());
        assertEquals("Correo o contraseña incorrectos.", excepcion.getMessage());
    }

    // ============================================================
    // BCRYPT: la contraseña se verifica con matches(), nunca equals
    // ============================================================
    @Test
    void laPasswordDebeVerificarseConBcrypt() {
        Usuario demo = usuarioDemo();
        String hashGuardado = demo.getPasswordHash();

        // El hash almacenado NO es la contraseña y sí es un hash BCrypt
        assertNotEquals("Demo123*", hashGuardado);
        assertTrue(hashGuardado.startsWith("$2"));

        when(usuarioRepository.findByCorreoIgnoreCase("demo@pulgatec.com"))
                .thenReturn(Optional.of(demo));

        LoginResponse respuesta =
                authService.login(solicitud("demo@pulgatec.com", "Demo123*"));

        assertNotNull(respuesta.getToken());
    }

    // ============================================================
    // TOKEN OPACO
    // ============================================================
    @Test
    void elTokenGeneradoNoDebeEstarVacio() {
        Usuario demo = usuarioDemo();
        when(usuarioRepository.findByCorreoIgnoreCase("demo@pulgatec.com"))
                .thenReturn(Optional.of(demo));

        String token = authService
                .login(solicitud("demo@pulgatec.com", "Demo123*"))
                .getToken();

        assertNotNull(token);
        assertFalse(token.isBlank());
        // Un UUID opaco ocupa 36 caracteres (8-4-4-4-12)
        assertEquals(36, token.length());
    }

    // ============================================================
    // LA RESPUESTA NUNCA EXPONE LA CONTRASEÑA
    // ============================================================
    @Test
    void laRespuestaNoDebeExponerPasswordNiHash() throws Exception {
        Usuario demo = usuarioDemo();
        when(usuarioRepository.findByCorreoIgnoreCase("demo@pulgatec.com"))
                .thenReturn(Optional.of(demo));

        LoginResponse respuesta =
                authService.login(solicitud("demo@pulgatec.com", "Demo123*"));

        String json = new ObjectMapper().writeValueAsString(respuesta);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("Password"));
        assertFalse(json.contains("$2")); // ni rastro del hash BCrypt
    }
}
