package com.ejemplo.backenduserapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 21 - POST /api/auth/login
 *
 * Prueba el endpoint real sobre H2 con el usuario demo creado por
 * ApplicationRunner (demo@pulgatec.com / Demo123*): 200 correcto,
 * 401 con credenciales inválidas (mismo mensaje sin revelar si la
 * cuenta existe) y 400 por validaciones Jakarta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String LOGIN = "/api/auth/login";

    // ============================================================
    // AUXILIARES
    // ============================================================
    private String cuerpo(String correo, String password) {
        return "{\"correo\":\"" + correo + "\",\"password\":\"" + password + "\"}";
    }

    private String loginYLeerMensaje(String correo, String password) throws Exception {
        String cuerpoRespuesta = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(correo, password)))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(cuerpoRespuesta);
        return json.path("messages").get(0).asText();
    }

    // ============================================================
    // 200 - LOGIN CORRECTO
    // ============================================================
    @Test
    void loginCorrectoDevuelve200ConTokenYUsuario() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("demo@pulgatec.com", "Demo123*")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.correo").value("demo@pulgatec.com"));
    }

    // ============================================================
    // 401 - CREDENCIALES INCORRECTAS
    // ============================================================
    @Test
    void passwordIncorrectaDevuelve401() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("demo@pulgatec.com", "PasswordMala1*")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.messages[0]")
                        .value("Correo o contraseña incorrectos."));
    }

    @Test
    void usuarioInexistenteDevuelve401() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("noexiste@correo.com", "CualquierClave1*")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.messages[0]")
                        .value("Correo o contraseña incorrectos."));
    }

    // ============================================================
    // 400 - VALIDACIONES JAKARTA
    // ============================================================
    @Test
    void correoVacioDevuelve400() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("", "Demo123*")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void passwordVaciaDevuelve400() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("demo@pulgatec.com", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void correoSinFormatoValidoDevuelve400() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("esto-no-es-un-correo", "Demo123*")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ============================================================
    // LA RESPUESTA NUNCA EXPONE DATOS SENSIBLES
    // ============================================================
    @Test
    void laRespuestaContieneElTokenOpaco() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("demo@pulgatec.com", "Demo123*")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.containsString("-")));
    }

    @Test
    void laRespuestaNoContienePassword() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("demo@pulgatec.com", "Demo123*")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void laRespuestaNoContienePasswordHash() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("demo@pulgatec.com", "Demo123*")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("$2"))));
    }

    // ============================================================
    // NO SE REVELA SI LA CUENTA EXISTE O NO
    // ============================================================
    @Test
    void elErrorDe401EsElMismoConOSinUsuario() throws Exception {
        String mensajePasswordMala =
                loginYLeerMensaje("demo@pulgatec.com", "PasswordMala1*");
        String mensajeUsuarioInexistente =
                loginYLeerMensaje("noexiste@correo.com", "CualquierClave1*");

        // Cuerpos idénticos: imposible distinguir un caso del otro
        assertEquals(mensajePasswordMala, mensajeUsuarioInexistente);
        assertEquals("Correo o contraseña incorrectos.", mensajePasswordMala);
    }
}
