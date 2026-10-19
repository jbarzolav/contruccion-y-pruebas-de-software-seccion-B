package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU 30 - Subida autenticada, validación y reemplazo de foto de perfil. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu30foto;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class FotoPerfilControllerTest {
    private static final Path SUBIDAS = carpetaTemporal();
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p1sAAAAASUVORK5CYII=");

    @DynamicPropertySource
    static void directorioDePrueba(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", SUBIDAS::toString);
    }

    private static Path carpetaTemporal() {
        try { return Files.createTempDirectory("pulgatec-hu30-"); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    private Usuario usuario;

    @BeforeEach
    void prepararCuenta() {
        usuario = new Usuario("foto-hu30@prueba.com", encoder.encode("Prueba123*"));
        usuario.setRol(Rol.CLIENTE);
        usuario = usuarios.saveAndFlush(usuario);
    }

    private String token() throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("correo", usuario.getCorreo(), "password", "Prueba123*"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(respuesta).get("token").asText();
    }

    private MockMultipartFile imagen(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, bytes);
    }

    @Test
    void subeFotoDelUsuarioAutenticadoYLaMuestraEnElPerfil() throws Exception {
        String token = token();
        mvc.perform(multipart("/api/usuarios/me/foto").file(imagen("perfil.png", PNG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.getId()))
                .andExpect(jsonPath("$.fotoUrl").value(org.hamcrest.Matchers.startsWith("/imagenes/perfiles/")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").isNotEmpty());
    }

    @Test
    void reemplazarFotoEliminaElArchivoAnterior() throws Exception {
        String token = token();
        String primera = mvc.perform(multipart("/api/usuarios/me/foto").file(imagen("perfil.png", PNG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ruta = mapper.readTree(primera).get("fotoUrl").asText();
        Path archivoAnterior = SUBIDAS.resolve(ruta.substring("/imagenes/".length()));
        assertTrue(Files.exists(archivoAnterior));
        mvc.perform(multipart("/api/usuarios/me/foto").file(imagen("nueva.png", PNG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertFalse(Files.exists(archivoAnterior));
    }

    @Test
    void requiereSesionValida() throws Exception {
        mvc.perform(multipart("/api/usuarios/me/foto").file(imagen("perfil.png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaFormatoNoPermitido() throws Exception {
        mvc.perform(multipart("/api/usuarios/me/foto").file(imagen("foto.txt", PNG))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rechazaArchivoQueNoContieneImagen() throws Exception {
        mvc.perform(multipart("/api/usuarios/me/foto").file(imagen("foto.png", "texto".getBytes()))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rechazaTamanoSuperiorADosMegabytes() throws Exception {
        mvc.perform(multipart("/api/usuarios/me/foto").file(imagen("foto.png", new byte[2 * 1024 * 1024 + 1]))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
