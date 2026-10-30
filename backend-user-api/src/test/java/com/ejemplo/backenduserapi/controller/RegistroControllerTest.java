package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 26 - POST /api/auth/registro
 *
 * Prueba el endpoint real sobre H2 (el runner demo sigue creando su
 * cuenta): 201 sin token, 400 por validaciones y 409 con correo
 * duplicado. Comprueba además que la contraseña queda como hash BCrypt
 * y que el rol siempre es CLIENTE.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistroControllerTest {

    private static final String REGISTRO = "/api/auth/registro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    private String cuerpo(String correo, String password, String confirmacion) {
        return "{\"nombre\":\"Nombre de prueba\",\"correo\":\"" + correo + "\",\"password\":\"" + password
                + "\",\"confirmacionPassword\":\"" + confirmacion + "\"}";
    }

    private void registrar(String correo, String password, String confirmacion) throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(correo, password, confirmacion)))
                .andExpect(status().isCreated());
    }

    // ============================================================
    // 201 - REGISTRO CORRECTO
    // ============================================================
    @Test
    void registroCorrectoDevuelve201ConMensajeSinToken() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("cliente-nuevo@pulgatec.com", "Clave123*", "Clave123*")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Cuenta creada correctamente."))
                // El registro no emite sesión automática.
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.rol").doesNotExist());
    }

    @Test
    void elCorreoSeNormalizaYLaContrasenaSeGuardaConBCrypt() throws Exception {
        registrar("Cliente.Mayus@Pulgatec.COM", "Clave123*", "Clave123*");

        Optional<Usuario> guardado = usuarios.findByCorreoIgnoreCase("cliente.mayus@pulgatec.com");
        assertTrue(guardado.isPresent(), "La cuenta debe existir con el correo normalizado");
        Usuario usuario = guardado.get();
        assertEquals("cliente.mayus@pulgatec.com", usuario.getCorreo());
        assertEquals("Nombre de prueba", usuario.getNombre());
        assertEquals(Rol.CLIENTE, usuario.getRol());
        assertNotEquals("Clave123*", usuario.getPasswordHash());
        assertTrue(encoder.matches("Clave123*", usuario.getPasswordHash()));
    }

    @Test
    void laRespuestaNuncaExponeLaContrasenaNiElHash() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("cliente-privado@pulgatec.com", "Clave123*", "Clave123*")))
                .andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("$2"))));
    }

    // ============================================================
    // ROL CLIENTE OBLIGATORIO
    // ============================================================
    @Test
    void noPermiteAsignarAdministradorDesdeElCuerpo() throws Exception {
        String conRol = "{\"nombre\":\"Intento Admin\",\"correo\":\"intento-admin@pulgatec.com\",\"password\":\"Clave123*\","
                + "\"confirmacionPassword\":\"Clave123*\",\"rol\":\"ADMINISTRADOR\"}";

        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conRol))
                .andExpect(status().isCreated());

        Usuario usuario = usuarios.findByCorreoIgnoreCase("intento-admin@pulgatec.com").orElseThrow();
        assertEquals(Rol.CLIENTE, usuario.getRol());
    }

    // ============================================================
    // 409 - CORREO DUPLICADO
    // ============================================================
    @Test
    void correoDuplicadoDevuelve409() throws Exception {
        registrar("repetido@pulgatec.com", "Clave123*", "Clave123*");

        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("repetido@pulgatec.com", "Otra123*", "Otra123*")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.messages[0]").value("El correo ya está registrado."));
    }

    @Test
    void correoDuplicadoSinDistinguirMayusculasDevuelve409() throws Exception {
        registrar("caja@pulgatec.com", "Clave123*", "Clave123*");

        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("CAJA@Pulgatec.com", "Clave123*", "Clave123*")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ============================================================
    // 400 - VALIDACIONES
    // ============================================================
    @Test
    void camposVaciosDevuelven400() throws Exception {
        mockMvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(cuerpo("", "Clave123*", "Clave123*")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" \",\"correo\":\"valido@pulgatec.com\",\"password\":\"Clave123*\","
                                + "\"confirmacionPassword\":\"Clave123*\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(cuerpo("vacio@pulgatec.com", "", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void correoSinFormatoValidoDevuelve400() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("esto-no-es-correo", "Clave123*", "Clave123*")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void contrasenasDiferentesDevuelven400() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("distintas@pulgatec.com", "Clave123*", "Otra123*")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages[0]").value("Las contraseñas no coinciden."));
    }

    @Test
    void passwordMenorA8CaracteresDevuelve400() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("corta@pulgatec.com", "Clave1*", "Clave1*")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void passwordMayorA72BytesDevuelve400() throws Exception {
        String larga = "a".repeat(73);
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("larga@pulgatec.com", larga, larga)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages[0]").value("La contraseña supera el límite de 72 bytes."));
    }

    // ============================================================
    // SEGURIDAD - ENDPOINT PÚBLICO Y SIN ELEVACIÓN DE PRIVILEGIOS
    // ============================================================
    @Test
    void elEndpointEsPublicoYNoDevuelvePermisosDeAdministracion() throws Exception {
        // Sin cabecera Authorization: el registro debe seguir funcionando.
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("publico@pulgatec.com", "Clave123*", "Clave123*")))
                .andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("ASIGNAR_ROLES"))))
                .andExpect(content().string(not(containsString("permisos"))));
    }
}
