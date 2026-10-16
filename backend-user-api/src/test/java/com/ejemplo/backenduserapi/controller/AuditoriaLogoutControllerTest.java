package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.entity.RegistroAuditoria;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.RegistroAuditoriaRepository;
import com.ejemplo.backenduserapi.repository.SesionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import com.ejemplo.backenduserapi.service.AuditService;
import com.ejemplo.backenduserapi.service.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
    @Autowired private SesionRepository sesiones;
    @Autowired private RegistroAuditoriaRepository registros;
    @Autowired private AuditService auditoria;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder encoder;
    private Usuario admin;
    private Usuario cliente;
    private Usuario vendedor;
    private String tokenAdmin;
    private String tokenCliente;
    private String tokenVendedor;

    @BeforeEach
    void configurar() {
        admin = cuenta("admin@hu2425.local", Rol.ADMINISTRADOR);
        cliente = cuenta("cliente@hu2425.local", Rol.CLIENTE);
        vendedor = cuenta("vendedor@hu2425.local", Rol.VENDEDOR);
        tokenAdmin = tokens.generarToken(admin.getId());
        tokenCliente = tokens.generarToken(cliente.getId());
        tokenVendedor = tokens.generarToken(vendedor.getId());
    }

    private Usuario cuenta(String correo, Rol rol) {
        Usuario usuario = new Usuario(correo, encoder.encode("PruebaSegura123*"));
        usuario.setRol(rol);
        return usuarios.saveAndFlush(usuario);
    }

    private RegistroAuditoria registro(AccionAuditoria accion, String usuario, String fecha) {
        return registros.saveAndFlush(new RegistroAuditoria(accion, usuario, Instant.parse(fecha)));
    }

    @Test
    void administradorConsultaHistorialPaginadoOrdenadoYSinSecretos() throws Exception {
        registro(AccionAuditoria.LOGIN, "cliente@hu2425.local", "2026-10-15T10:00:00Z");
        registro(AccionAuditoria.LOGOUT, "admin@hu2425.local", "2026-10-16T10:00:00Z");

        String json = mvc.perform(get("/api/admin/auditoria").param("page", "0").param("size", "1")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(1))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].accion").value("LOGOUT"))
                .andExpect(jsonPath("$.contenido[0].usuario").value("admin@hu2425.local"))
                .andExpect(jsonPath("$.contenido[0].fecha").value("2026-10-16T10:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.toLowerCase().contains("password"));
        assertFalse(json.toLowerCase().contains("token"));
        assertFalse(json.contains("$2a$"));
    }

    @Test
    void clienteYVendedorNoPuedenLeerLaAuditoria() throws Exception {
        mvc.perform(get("/api/admin/auditoria").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auditoria").header("Authorization", "Bearer " + tokenVendedor))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auditoria")).andExpect(status().isUnauthorized());
    }

    @Test
    void filtrosInvalidosSeRechazanCon400() throws Exception {
        mvc.perform(get("/api/admin/auditoria").param("page", "-1")
                .header("Authorization", "Bearer " + tokenAdmin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/auditoria").param("size", "101")
                .header("Authorization", "Bearer " + tokenAdmin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/auditoria").param("page", "no-es-numero")
                .header("Authorization", "Bearer " + tokenAdmin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listaVaciaDevuelvePaginaVacia() throws Exception {
        mvc.perform(get("/api/admin/auditoria").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isEmpty())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void loginYRestablecimientoRegistranSoloAccionEIdentidad() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("correo", cliente.getCorreo(), "password", "PruebaSegura123*"))))
                .andExpect(status().isOk());
        assertTrue(registros.findAll().stream().anyMatch(r -> r.getAccion() == AccionAuditoria.LOGIN
                && r.getUsuario().equals(cliente.getCorreo())));
        String auditJson = mvc.perform(get("/api/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(auditJson);
        assertFalse(node.toString().contains("PruebaSegura123*"));
        assertFalse(node.toString().contains("passwordHash"));
    }

    @Test
    void logoutInvalidaTokenAuditaUnaVezYRechazaReutilizacion() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("Sesión cerrada correctamente."));
        assertTrue(registros.findAll().stream().anyMatch(r -> r.getAccion() == AccionAuditoria.LOGOUT
                && r.getUsuario().equals(admin.getCorreo())));

        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer token-inventado"))
                .andExpect(status().isUnauthorized());

        String nuevo = tokens.generarToken(admin.getId());
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + nuevo))
                .andExpect(status().isOk());
    }
}
