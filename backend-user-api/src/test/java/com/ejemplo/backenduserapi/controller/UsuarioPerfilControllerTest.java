package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.SesionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 27 - GET /api/usuarios/me
 *
 * Perfil del usuario autenticado sobre H2 aislada: 200 con sus propios datos,
 * 401 sin sesión o con token inválido/vencido, y sin exponer contraseña ni hash.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu27perfil;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class UsuarioPerfilControllerTest {

    private static final String PERFIL = "/api/usuarios/me";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SesionRepository sesiones;
    @Autowired private PasswordEncoder encoder;

    private Usuario cliente;
    private Usuario vendedor;

    @BeforeEach
    void configurar() {
        cliente = cuenta("perfil-hu27@prueba.com", Rol.CLIENTE);
        vendedor = cuenta("otro-hu27@prueba.com", Rol.VENDEDOR);
    }

    private Usuario cuenta(String correo, Rol rol) {
        Usuario usuario = new Usuario(correo, encoder.encode("Prueba123*"));
        usuario.setRol(rol);
        return usuarios.saveAndFlush(usuario);
    }

    private String token(Usuario usuario) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("correo", usuario.getCorreo(), "password", "Prueba123*"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(json).get("token").asText();
    }

    // ============================================================
    // A. USUARIO AUTENTICADO -> 200
    // ============================================================
    @Test
    void sesionAutenticadaDevuelve200ConLosDatosPropios() throws Exception {
        mvc.perform(get(PERFIL).header("Authorization", "Bearer " + token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("perfil-hu27@prueba.com"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.permisos[0]").value("COMPRAR"));
    }

    // ============================================================
    // B. SESIÓN AUSENTE -> 401
    // ============================================================
    @Test
    void sinSesionDevuelve401() throws Exception {
        mvc.perform(get(PERFIL)).andExpect(status().isUnauthorized());
    }

    @Test
    void cabeceraSinEsquemaBearerDevuelve401() throws Exception {
        mvc.perform(get(PERFIL).header("Authorization", "no-es-bearer"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // C. TOKEN INVÁLIDO / VENCIDO -> 401
    // ============================================================
    @Test
    void tokenInventadoDevuelve401() throws Exception {
        mvc.perform(get(PERFIL).header("Authorization", "Bearer inventado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoDevuelve401() throws Exception {
        sesiones.guardar("token-vencido-hu27", cliente.getId(), Instant.now().minusSeconds(1));
        mvc.perform(get(PERFIL).header("Authorization", "Bearer token-vencido-hu27"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // D. LA RESPUESTA NO EXPONE INFORMACIÓN SENSIBLE
    // ============================================================
    @Test
    void laRespuestaNoContieneContrasenaNiHash() throws Exception {
        mvc.perform(get(PERFIL).header("Authorization", "Bearer " + token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("$2"))));
    }

    // ============================================================
    // E. IDENTIDAD: SOLO LOS DATOS DEL USUARIO AUTENTICADO
    // ============================================================
    @Test
    void soloDevuelveLosDatosDelUsuarioAutenticado() throws Exception {
        String cuerpoCliente = mvc.perform(get(PERFIL).header("Authorization", "Bearer " + token(cliente)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String cuerpoVendedor = mvc.perform(get(PERFIL).header("Authorization", "Bearer " + token(vendedor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode jsonCliente = mapper.readTree(cuerpoCliente);
        JsonNode jsonVendedor = mapper.readTree(cuerpoVendedor);

        // Cada sesión recibe exactamente su propia identidad.
        assertEquals(cliente.getId(), jsonCliente.get("id").asLong());
        assertEquals("perfil-hu27@prueba.com", jsonCliente.get("correo").asText());
        assertEquals("CLIENTE", jsonCliente.get("rol").asText());

        assertEquals(vendedor.getId(), jsonVendedor.get("id").asLong());
        assertEquals("otro-hu27@prueba.com", jsonVendedor.get("correo").asText());
        assertEquals("VENDEDOR", jsonVendedor.get("rol").asText());

        // Sin mezclar datos entre cuentas.
        assertNotEquals(jsonCliente.get("correo").asText(), jsonVendedor.get("correo").asText());
        assertFalse(cuerpoCliente.contains("otro-hu27@prueba.com"));
        assertFalse(cuerpoVendedor.contains("perfil-hu27@prueba.com"));
    }
}
