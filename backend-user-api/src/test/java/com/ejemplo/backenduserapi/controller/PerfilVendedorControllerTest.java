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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 29 - GET/PATCH /api/vendedores/me/perfil
 *
 * Sobre H2 aislada: 200 para VENDEDOR (crear y actualizar), 400 datos
 * inválidos, 401 sin sesión y 403 para CLIENTE/ADMINISTRADOR. Nunca se
 * exponen secretos ni se admite elegir a otro vendedor.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu29vendedor;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class PerfilVendedorControllerTest {

    private static final String PERFIL = "/api/vendedores/me/perfil";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;

    private Usuario vendedor;
    private Usuario vendedorAjeno;
    private Usuario cliente;
    private Usuario administrador;

    @BeforeEach
    void configurar() {
        vendedor = cuenta("vendedor-hu29@prueba.com", Rol.VENDEDOR);
        vendedorAjeno = cuenta("vendedor-ajeno@prueba.com", Rol.VENDEDOR);
        cliente = cuenta("cliente-hu29@prueba.com", Rol.CLIENTE);
        administrador = cuenta("admin-hu29@prueba.com", Rol.ADMINISTRADOR);
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

    private static final String CUERPO_VALIDO =
            "{\"nombreComercial\":\"TecnoAna\",\"descripcion\":\"Celulares y accesorios.\",\"telefonoContacto\":\"8095551234\"}";

    // ============================================================
    // A. VENDEDOR -> 200 (crear, consultar y actualizar)
    // ============================================================
    @Test
    void vendedorGuardaPerfilDevuelve200() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(vendedor))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(vendedor.getId()))
                .andExpect(jsonPath("$.nombreComercial").value("TecnoAna"))
                .andExpect(jsonPath("$.descripcion").value("Celulares y accesorios."))
                .andExpect(jsonPath("$.telefonoContacto").value("8095551234"));
    }

    @Test
    void getSinPerfilDevuelveCamposVacios() throws Exception {
        mvc.perform(get(PERFIL).header("Authorization", "Bearer " + token(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(vendedor.getId()))
                .andExpect(jsonPath("$.nombreComercial").value(""));
    }

    @Test
    void getTrasElPatchDevuelveElPerfilPersistido() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(vendedor))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk());

        mvc.perform(get(PERFIL).header("Authorization", "Bearer " + token(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("TecnoAna"));
    }

    @Test
    void segundoPatchActualizaElMismoPerfil() throws Exception {
        String bearer = "Bearer " + token(vendedor);
        mvc.perform(patch(PERFIL).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk());

        mvc.perform(patch(PERFIL).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreComercial\":\"TecnoAna Pro\",\"descripcion\":\"Ahora con envíos.\",\"telefonoContacto\":\"8095559999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("TecnoAna Pro"))
                .andExpect(jsonPath("$.telefonoContacto").value("8095559999"));
    }

    // ============================================================
    // B. DATOS INVÁLIDOS -> 400
    // ============================================================
    @Test
    void datosInvalidosDevuelven400() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(vendedor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreComercial\":\"   \",\"descripcion\":\"\",\"telefonoContacto\":\"no-telefono\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ============================================================
    // C. SIN SESIÓN -> 401
    // ============================================================
    @Test
    void sinSesionDevuelve401() throws Exception {
        mvc.perform(patch(PERFIL).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PERFIL)).andExpect(status().isUnauthorized());
    }

    // ============================================================
    // D. NO VENDEDOR -> 403
    // ============================================================
    @Test
    void clienteDevuelve403() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(cliente))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
        mvc.perform(get(PERFIL).header("Authorization", "Bearer " + token(cliente)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDevuelve403() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(administrador))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // E. PROPIEDAD Y SEGURIDAD
    // ============================================================
    @Test
    void cadaVendedorSoloVeYSuPerfilaElSuyo() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(vendedor))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk());

        // El vendedor ajeno consulta su propio perfil: sigue vacío.
        mvc.perform(get(PERFIL).header("Authorization", "Bearer " + token(vendedorAjeno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(vendedorAjeno.getId()))
                .andExpect(jsonPath("$.nombreComercial").value(""));
    }

    @Test
    void elBodyNoPuedeAsignarOtroPropietario() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(vendedor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + vendedorAjeno.getId()
                                + ",\"nombreComercial\":\"TecnoAna\",\"descripcion\":\"Desc.\",\"telefonoContacto\":\"8095551234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(vendedor.getId()));
    }

    // ============================================================
    // F. LA RESPUESTA NO EXPONE INFORMACIÓN SENSIBLE
    // ============================================================
    @Test
    void laRespuestaNoExponeSecretos() throws Exception {
        mvc.perform(patch(PERFIL).header("Authorization", "Bearer " + token(vendedor))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(content().string(not(containsString("$2"))));
    }
}
