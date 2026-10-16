package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.*;
import com.ejemplo.backenduserapi.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HU 23 - Autorización real con sesiones de login y base H2 aislada. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu23autorizacion;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class AutorizacionControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProductoRepository productos;
    @Autowired private CarritoRepository carritos;
    @Autowired private ItemCarritoRepository items;
    @Autowired private SesionRepository sesiones;
    @Autowired private PasswordEncoder encoder;
    private Usuario admin;
    private Usuario cliente;
    private Usuario vendedor;

    @BeforeEach
    void configurar() {
        admin = cuenta("admin-hu23@prueba.com", Rol.ADMINISTRADOR);
        cliente = cuenta("cliente-hu23@prueba.com", Rol.CLIENTE);
        vendedor = cuenta("vendedor-hu23@prueba.com", Rol.VENDEDOR);
    }

    private Usuario cuenta(String correo, Rol rol) {
        Usuario usuario = new Usuario(correo, encoder.encode("Prueba123*"));
        usuario.setRol(rol);
        return usuarios.saveAndFlush(usuario);
    }

    private String token(Usuario usuario) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("correo", usuario.getCorreo(), "password", "Prueba123*"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("token").asText();
    }

    private ResultActions asignar(String token, Long id, String rol) throws Exception {
        return mvc.perform(patch("/api/admin/usuarios/{id}/rol", id)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("rol", rol))));
    }

    private Producto producto() {
        Producto p = new Producto();
        p.setNombre("Producto HU23"); p.setDescripcion("Prueba de permisos");
        p.setPrecio(new BigDecimal("20.00")); p.setStock(5); p.setCategoria("Otros");
        p.setEstado("DISPONIBLE"); p.setImagenUrl("prueba.png"); p.setVendedorId(vendedor.getId());
        return productos.saveAndFlush(p);
    }

    @Test
    void administradorAsignaRolYDevuelvePermisosSinPassword() throws Exception {
        asignar(token(admin), cliente.getId(), "VENDEDOR").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("VENDEDOR"))
                .andExpect(jsonPath("$.permisos[0]").value("GESTIONAR_PRODUCTOS"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        assertEquals(Rol.VENDEDOR, usuarios.findById(cliente.getId()).orElseThrow().getRol());
    }

    @Test
    void rolDesconocidoDevuelve400YConservaRol() throws Exception {
        asignar(token(admin), cliente.getId(), "DESCONOCIDO").andExpect(status().isBadRequest());
        assertEquals(Rol.CLIENTE, cliente.getRol());
    }

    @Test
    void rolVacioDevuelve400() throws Exception {
        asignar(token(admin), cliente.getId(), "").andExpect(status().isBadRequest());
    }

    @Test
    void usuarioInexistenteDevuelve404() throws Exception {
        asignar(token(admin), Long.MAX_VALUE, "CLIENTE").andExpect(status().isNotFound());
    }

    @Test
    void sinSesionNoPermiteAsignarRoles() throws Exception {
        mvc.perform(patch("/api/admin/usuarios/{id}/rol", cliente.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInventadoDevuelve401() throws Exception {
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer inventado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoDevuelve401() throws Exception {
        sesiones.guardar("token-vencido-hu23", admin.getId(), Instant.now().minusSeconds(1));
        mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer token-vencido-hu23"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNoPuedeAsignarseAdministrador() throws Exception {
        asignar(token(cliente), cliente.getId(), "ADMINISTRADOR").andExpect(status().isForbidden());
        assertEquals(Rol.CLIENTE, cliente.getRol());
    }

    @Test
    void vendedorNoPuedeAsignarRoles() throws Exception {
        asignar(token(vendedor), cliente.getId(), "VENDEDOR").andExpect(status().isForbidden());
    }

    @Test
    void listadoAdministrativoNoExponeHashes() throws Exception {
        String respuesta = mvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + token(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(respuesta.contains("password"));
        assertFalse(respuesta.contains("$2"));
    }

    @Test
    void cambioDeRolSeAplicaASesionYaExistente() throws Exception {
        String sesion = token(vendedor);
        mvc.perform(get("/api/vendedores/me/productos").param("vendedorId", vendedor.getId().toString())
                .header("Authorization", "Bearer " + sesion)).andExpect(status().isOk());
        asignar(token(admin), vendedor.getId(), "CLIENTE").andExpect(status().isOk());
        mvc.perform(get("/api/vendedores/me/productos").param("vendedorId", vendedor.getId().toString())
                .header("Authorization", "Bearer " + sesion)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + sesion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void clienteNoPuedePublicarYVendedorNoPuedeComprar() throws Exception {
        mvc.perform(post("/api/productos").header("Authorization", "Bearer " + token(cliente))
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(get("/api/carrito/items").header("Authorization", "Bearer " + token(vendedor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNoPuedeLeerCarritoDeOtroUsuario() throws Exception {
        mvc.perform(get("/api/carrito/items").param("clienteId", admin.getId().toString())
                .header("Authorization", "Bearer " + token(cliente))).andExpect(status().isForbidden());
    }

    @Test
    void clienteNoPuedeModificarNiEliminarItemAjeno() throws Exception {
        Carrito carrito = carritos.save(new Carrito(admin.getId()));
        ItemCarrito item = items.saveAndFlush(new ItemCarrito(carrito, producto(), 1, new BigDecimal("20.00")));
        String sesion = token(cliente);
        mvc.perform(put("/api/carrito/items/{id}", item.getId()).header("Authorization", "Bearer " + sesion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":2}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/carrito/items/{id}", item.getId()).header("Authorization", "Bearer " + sesion))
                .andExpect(status().isForbidden());
        assertEquals(1, items.findById(item.getId()).orElseThrow().getCantidad());
    }

    @Test
    void vendedorNoPuedeSuplantarPropietarioEnElCuerpo() throws Exception {
        Map<String, Object> datos = Map.of("nombre", "Prueba", "descripcion", "Producto", "precio", 10,
                "stock", 1, "categoria", "Otros", "estado", "DISPONIBLE", "imagenUrl", "prueba.png", "vendedorId", cliente.getId());
        mvc.perform(post("/api/productos").header("Authorization", "Bearer " + token(vendedor))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(datos)))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogoPermanecePublico() throws Exception {
        mvc.perform(get("/api/productos/catalogo")).andExpect(status().isOk());
    }
}
