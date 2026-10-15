package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.TokenRecuperacion;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.TokenRecuperacionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import com.ejemplo.backenduserapi.service.CorreoRecuperacionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HU 22 - Endpoints reales con H2; solo se sustituye el envío del correo local. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecuperacionControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private TokenRecuperacionRepository tokens;
    @Autowired private PasswordEncoder encoder;
    @MockitoBean private CorreoRecuperacionService correo;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        usuario = usuarios.save(new Usuario("hu22-prueba@pulgatec.com", encoder.encode("Anterior123*")));
    }

    private ResultActions enviar(String ruta, Map<String, String> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/auth/" + ruta)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(cuerpo)));
    }

    private Map<String, String> cambio(String token, String confirmacion) {
        return Map.of("token", token, "password", "Nueva123*", "confirmacionPassword", confirmacion);
    }

    private String solicitarToken() throws Exception {
        enviar("recuperacion", Map.of("correo", usuario.getCorreo())).andExpect(status().isOk());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq(usuario.getCorreo()), captor.capture());
        return captor.getValue();
    }

    @Test
    void solicitudDevuelveMismoMensajeSinExponerToken() throws Exception {
        String registrado = enviar("recuperacion", Map.of("correo", usuario.getCorreo()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String inexistente = enviar("recuperacion", Map.of("correo", "nadie@pulgatec.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(registrado), mapper.readTree(inexistente));
        verify(correo, times(1)).enviar(eq(usuario.getCorreo()), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void correoVacioDevuelve400() throws Exception {
        enviar("recuperacion", Map.of("correo", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(correo);
    }

    @Test
    void correoInvalidoDevuelve400() throws Exception {
        enviar("recuperacion", Map.of("correo", "correo-invalido")).andExpect(status().isBadRequest());
        verifyNoInteractions(correo);
    }

    @Test
    void camposObligatoriosAusentesDevuelven400() throws Exception {
        enviar("restablecer", Map.of()).andExpect(status().isBadRequest());
        enviar("restablecer", cambio("", "Nueva123*")).andExpect(status().isBadRequest());
        enviar("restablecer", Map.of("token", "token", "password", "", "confirmacionPassword", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenInvalidoDevuelve400() throws Exception {
        enviar("restablecer", cambio("inexistente", "Nueva123*"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Recuperación inválida"));
        assertTrue(encoder.matches("Anterior123*", usuarios.findById(usuario.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    void tokenVencidoDevuelve400SinCambiarPassword() throws Exception {
        tokens.saveAndFlush(new TokenRecuperacion("vencido", usuario, Instant.now().minusSeconds(60)));
        enviar("restablecer", cambio("vencido", "Nueva123*")).andExpect(status().isBadRequest());
        assertTrue(encoder.matches("Anterior123*", usuarios.findById(usuario.getId()).orElseThrow().getPasswordHash()));
    }

    @Test
    void contrasenasDistintasDevuelven400YPermitenReintentar() throws Exception {
        String token = solicitarToken();
        enviar("restablecer", cambio(token, "Distinta123*"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.messages[0]").value("Las contraseñas no coinciden."));
        assertTrue(tokens.existsById(token));
        enviar("restablecer", cambio(token, "Nueva123*")).andExpect(status().isOk());
    }

    @Test
    void cambioCorrectoPermiteLoginSoloConNuevaPassword() throws Exception {
        String token = solicitarToken();
        enviar("restablecer", cambio(token, "Nueva123*"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente."));
        enviar("login", Map.of("correo", usuario.getCorreo(), "password", "Nueva123*"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        enviar("login", Map.of("correo", usuario.getCorreo(), "password", "Anterior123*"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenConsumidoNoPuedeReutilizarse() throws Exception {
        String token = solicitarToken();
        enviar("restablecer", cambio(token, "Nueva123*")).andExpect(status().isOk());
        assertFalse(tokens.existsById(token));
        enviar("restablecer", cambio(token, "Nueva123*")).andExpect(status().isBadRequest());
    }

    @Test
    void cambioInvalidaOtrosEnlacesPendientesDelUsuario() throws Exception {
        String token = solicitarToken();
        tokens.saveAndFlush(new TokenRecuperacion("otro-enlace", usuario, Instant.now().plusSeconds(60)));
        enviar("restablecer", cambio(token, "Nueva123*")).andExpect(status().isOk());
        assertFalse(tokens.existsById("otro-enlace"));
        enviar("restablecer", cambio("otro-enlace", "Nueva123*")).andExpect(status().isBadRequest());
    }
}
