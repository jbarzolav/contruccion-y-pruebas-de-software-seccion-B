package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
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
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 28 - PATCH /api/usuarios/me
 *
 * Sobre H2 aislada: 200 con actualización válida, 400 con datos inválidos,
 * 401 sin sesión, 409 con correo de otra cuenta e imposibilidad de alterar
 * datos ajenos o protegidos (rol, hash) desde el body.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu28edicion;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class UsuarioEdicionPerfilControllerTest {

    private static final String PERFIL = "/api/usuarios/me";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;

    private Usuario cliente;
    private Usuario otro;

    @BeforeEach
    void configurar() {
        cliente = cuenta("edithu28@prueba.com", Rol.CLIENTE);
        otro = cuenta("otro-hu28@prueba.com", Rol.VENDEDOR);
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
    // A. ACTUALIZACIÓN VÁLIDA -> 200 Y PERSISTENCIA
    // ============================================================
    @Test
    void actualizacionValidaDevuelve200YPersiste() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana Pérez\",\"correo\":\"nuevo-hu28@prueba.com\",\"telefono\":\"809-555-1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cliente.getId()))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.correo").value("nuevo-hu28@prueba.com"))
                .andExpect(jsonPath("$.telefono").value("809-555-1234"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));

        // HU 27 - GET /me devuelve los datos persistidos tras el PATCH.
        Usuario recargado = usuarios.findById(cliente.getId()).orElseThrow();
        assertEquals("Ana Pérez", recargado.getNombre());
        assertEquals("nuevo-hu28@prueba.com", recargado.getCorreo());
        assertEquals("809-555-1234", recargado.getTelefono());
    }

    // ============================================================
    // B. DATOS INVÁLIDOS -> 400
    // ============================================================
    @Test
    void datosInvalidosDevuelven400() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"   \",\"correo\":\"no-es-correo\",\"telefono\":\"llamame\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ============================================================
    // C. SIN SESIÓN -> 401
    // ============================================================
    @Test
    void sinSesionDevuelve401() throws Exception {
        mvc.perform(patch(PERFIL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"a@prueba.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInventadoDevuelve401() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer inventado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"a@prueba.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // D. PROPIEDAD: NO SE PUEDEN ALTERAR DATOS AJENOS NI PROTEGIDOS
    // ============================================================
    @Test
    void elBodyNoPuedeElegirOtroUsuario() throws Exception {
        // Envía el id de la víctima: el DTO lo ignora y la sesión decide.
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + otro.getId() + ",\"nombre\":\"Ana\",\"correo\":\"ana-hu28@prueba.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cliente.getId()));

        Usuario victima = usuarios.findById(otro.getId()).orElseThrow();
        assertEquals("otro-hu28@prueba.com", victima.getCorreo());
        assertNotEquals("Ana", victima.getNombre());
    }

    @Test
    void elBodyNoPuedeCambiarRolNiHash() throws Exception {
        String hashAntes = cliente.getPasswordHash();

        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"edithu28@prueba.com\",\"rol\":\"ADMINISTRADOR\",\"passwordHash\":\"hackeado\",\"permisos\":[\"ASIGNAR_ROLES\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.permisos[0]").value("COMPRAR"));

        Usuario despues = usuarios.findById(cliente.getId()).orElseThrow();
        assertEquals(Rol.CLIENTE, despues.getRol());
        assertEquals(hashAntes, despues.getPasswordHash());
    }

    @Test
    void correoDeOtraCuentaDevuelve409() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"otro-hu28@prueba.com\"}"))
                .andExpect(status().isConflict());
    }

    // ============================================================
    // E. LA RESPUESTA NO EXPONE INFORMACIÓN SENSIBLE
    // ============================================================
    @Test
    void laRespuestaNoContieneContrasenaNiHash() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana Pérez\",\"correo\":\"edithu28@prueba.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString("$2"))));
    }
}
