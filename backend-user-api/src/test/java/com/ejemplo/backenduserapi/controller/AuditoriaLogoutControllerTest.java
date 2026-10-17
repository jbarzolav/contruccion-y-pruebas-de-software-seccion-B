package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.RegistroAuditoriaRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import com.ejemplo.backenduserapi.service.AuditService;
import com.ejemplo.backenduserapi.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu2425auditoria;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class AuditoriaLogoutControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RegistroAuditoriaRepository registros;
    @Autowired private AuditService auditoria;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder encoder;
    private Usuario admin;
    private String tokenAdmin;

    @BeforeEach
    void configurar() {
        admin = new Usuario("admin@hu2425.local", encoder.encode("PruebaSegura123*"));
        admin.setRol(Rol.ADMINISTRADOR);
        admin = usuarios.saveAndFlush(admin);
        tokenAdmin = tokens.generarToken(admin.getId());
    }

    @Test
    void auditoriaInternaPersisteAccionEIdentidadSinCredenciales() {
        auditoria.registrar(admin, AccionAuditoria.CAMBIO_ROL);
        var registro = registros.findAll().getFirst();
        assertEquals(AccionAuditoria.CAMBIO_ROL, registro.getAccion());
        assertEquals(admin.getCorreo(), registro.getUsuario());
        assertFalse(registro.toString().contains("PruebaSegura123*"));
    }

    @Test
    void loginRegistraSoloAccionEIdentidad() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("correo", admin.getCorreo(), "password", "PruebaSegura123*"))))
                .andExpect(status().isOk());
        assertTrue(registros.findAll().stream().anyMatch(r -> r.getAccion() == AccionAuditoria.LOGIN
                && r.getUsuario().equals(admin.getCorreo())));
        assertFalse(registros.findAll().toString().contains("PruebaSegura123*"));
    }

    @Test
    void logoutInvalidaTokenAuditaYRechazaReutilizacion() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("Sesión cerrada correctamente."));
        assertTrue(registros.findAll().stream().anyMatch(r -> r.getAccion() == AccionAuditoria.LOGOUT
                && r.getUsuario().equals(admin.getCorreo())));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isUnauthorized());
    }
}
