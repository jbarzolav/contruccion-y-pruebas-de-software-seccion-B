package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.FavoritoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU 31 - Alta idempotente y consulta de productos favoritos. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FavoritoControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProductoRepository productos;
    @Autowired private FavoritoRepository favoritos;
    @Autowired private PasswordEncoder encoder;

    private Usuario cliente;
    private Producto producto;

    @BeforeEach
    void prepararDatos() {
        cliente = new Usuario("favorito-hu31@prueba.com", encoder.encode("Prueba123*"));
        cliente.setRol(Rol.CLIENTE);
        cliente = usuarios.saveAndFlush(cliente);
        producto = productos.saveAndFlush(new Producto(null, "Producto favorito", "Prueba HU31",
                new BigDecimal("12.50"), 4, "Accesorios", "DISPONIBLE", null, 99L));
    }

    private String token() throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "correo", cliente.getCorreo(), "password", "Prueba123*"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(respuesta).get("token").asText();
    }

    @Test
    void guardaFavoritoYLoDevuelveEnLaListaDelCliente() throws Exception {
        String token = token();
        mvc.perform(post("/api/usuarios/me/favoritos/{idProducto}", producto.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(producto.getId()))
                .andExpect(jsonPath("$.favorito").value(true));

        mvc.perform(get("/api/usuarios/me/favoritos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(producto.getId()));
    }

    @Test
    void repetirAltaNoCreaUnFavoritoDuplicado() throws Exception {
        String token = token();
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(post("/api/usuarios/me/favoritos/{idProducto}", producto.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.favorito").value(true));
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, favoritos.count());
    }

    @Test
    void rechazaProductoInexistente() throws Exception {
        mvc.perform(post("/api/usuarios/me/favoritos/{idProducto}", 999999L)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void requiereSesionAutenticada() throws Exception {
        mvc.perform(post("/api/usuarios/me/favoritos/{idProducto}", producto.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eliminaFavoritoSinEliminarElProducto() throws Exception {
        String token = token();
        mvc.perform(post("/api/usuarios/me/favoritos/{idProducto}", producto.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/usuarios/me/favoritos/{idProducto}", producto.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(0, favoritos.count());
        org.junit.jupiter.api.Assertions.assertTrue(productos.existsById(producto.getId()));
    }

    @Test
    void eliminarFavoritoRepetidoDevuelve404YNo500() throws Exception {
        String token = token();
        mvc.perform(delete("/api/usuarios/me/favoritos/{idProducto}", producto.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void eliminarFavoritoRequiereSesion() throws Exception {
        mvc.perform(delete("/api/usuarios/me/favoritos/{idProducto}", producto.getId()))
                .andExpect(status().isUnauthorized());
    }
}
