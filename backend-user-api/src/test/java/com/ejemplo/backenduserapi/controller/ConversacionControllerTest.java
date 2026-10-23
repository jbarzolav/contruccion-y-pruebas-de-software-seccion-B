package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.*;
import com.ejemplo.backenduserapi.repository.*;
import com.ejemplo.backenduserapi.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Sin @Transactional: cada petición usa su transacción real y el DTO debe estar materializado.
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:consultas_test;DB_CLOSE_DELAY=-1", "app.conversaciones.longitud-maxima=80"})
@AutoConfigureMockMvc
class ConversacionControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UsuarioRepository usuarios;
    @Autowired ProductoRepository productos;
    @Autowired ConversacionRepository conversaciones;
    @Autowired MensajeRepository mensajes;
    @Autowired TokenService tokens;
    Usuario cliente, vendedor, tercero;
    Producto producto;
    String tc, tv, tt;

    Usuario usuario(Rol rol) {
        Usuario u = new Usuario(UUID.randomUUID() + "@prueba.com", "hash-no-publico"); u.setRol(rol); return usuarios.saveAndFlush(u);
    }
    @BeforeEach void preparar() {
        cliente = usuario(Rol.CLIENTE); vendedor = usuario(Rol.VENDEDOR); tercero = usuario(Rol.VENDEDOR);
        tc = tokens.generarToken(cliente.getId()); tv = tokens.generarToken(vendedor.getId()); tt = tokens.generarToken(tercero.getId());
        producto = productos.saveAndFlush(new Producto(null, "Producto consulta", "Descripción", BigDecimal.TEN, 5, "Tecnología", "DISPONIBLE", null, vendedor.getId()));
    }
    @AfterEach void limpiar() {
        mensajes.deleteAll(); conversaciones.deleteAll(); productos.deleteById(producto.getId());
        usuarios.deleteAllById(java.util.List.of(cliente.getId(), vendedor.getId(), tercero.getId()));
        tokens.invalidar(tc); tokens.invalidar(tv); tokens.invalidar(tt);
    }
    long consultar() throws Exception {
        String json = mvc.perform(post("/api/conversaciones").header("Authorization", "Bearer " + tc).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("productoId", producto.getId(), "mensaje", "  Incluye cables?  ", "clienteId", tercero.getId(), "vendedorId", tercero.getId()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mensajes[0].emisorId").value(cliente.getId()))
                .andExpect(jsonPath("$.mensajes[0].contenido").value("Incluye cables?"))
                .andExpect(jsonPath("$.mensajes[0].fecha").isNotEmpty())
                .andExpect(jsonPath("$..passwordHash").doesNotExist()).andExpect(jsonPath("$..token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("idConversacion").asLong();
    }
    @Test void flujoPersistenteReutilizaYRespondeSinAlterarProducto() throws Exception {
        long id = consultar(); assertEquals(id, consultar()); assertEquals(1, conversaciones.count()); assertEquals(2, mensajes.count());
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tv).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensaje\":\"Sí, incluye cables\",\"emisorId\":999}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.emisorId").value(vendedor.getId())).andExpect(jsonPath("$.fecha").isNotEmpty());
        mvc.perform(get("/api/conversaciones/{id}", id).header("Authorization", "Bearer " + tc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensajes.length()").value(3));
        mvc.perform(get("/api/conversaciones").header("Authorization", "Bearer " + tv))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idConversacion").value(id));
        assertEquals(5, productos.findById(producto.getId()).orElseThrow().getStock());
        assertEquals("DISPONIBLE", productos.findById(producto.getId()).orElseThrow().getEstado());
    }
    @Test void terceroNoLeeNiRespondeYListaVacia() throws Exception {
        long id = consultar();
        mvc.perform(get("/api/conversaciones/{id}", id).header("Authorization", "Bearer " + tt)).andExpect(status().isForbidden());
        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tt)).andExpect(status().isForbidden());
        mvc.perform(get("/api/conversaciones").header("Authorization", "Bearer " + tt)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"Intruso\"}")).andExpect(status().isForbidden());
        assertEquals(1, mensajes.count());
    }
    @Test void mensajeInvalidoEnConsultaYRespuesta() throws Exception {
        long id = consultar();
        for (String texto : java.util.List.of("", "  ", "x".repeat(81))) {
            mvc.perform(post("/api/conversaciones").header("Authorization", "Bearer " + tc).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("productoId", producto.getId(), "mensaje", texto)))).andExpect(status().isBadRequest());
            mvc.perform(post("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tv).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("mensaje", texto)))).andExpect(status().isBadRequest());
        }
    }
    @Test void sinSesionOTokenInvalido() throws Exception {
        for (String token : java.util.List.of("", "Bearer invalido")) {
            mvc.perform(post("/api/conversaciones").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/conversaciones/1/mensajes").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/conversaciones").header("Authorization", token)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/conversaciones/1/mensajes").header("Authorization", token)).andExpect(status().isUnauthorized());
        }
    }

    @Test void muestraUltimoMensajeYPaginaElHistorialEnOrdenCronologico() throws Exception {
        long id = consultar();
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tv)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"Sí, incluye cables.\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/conversaciones").header("Authorization", "Bearer " + tc)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("productoId", producto.getId(), "mensaje", "¿Qué medida tiene?"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/conversaciones").header("Authorization", "Bearer " + tc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idConversacion").value(id))
                .andExpect(jsonPath("$[0].ultimoMensaje.contenido").value("¿Qué medida tiene?"));

        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tc)
                        .param("pagina", "0").param("tamano", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajes.length()").value(2))
                .andExpect(jsonPath("$.mensajes[0].contenido").value("¿Qué medida tiene?"))
                .andExpect(jsonPath("$.mensajes[1].contenido").value("Sí, incluye cables."))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.tieneSiguiente").value(true));

        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tc)
                        .param("pagina", "1").param("tamano", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajes.length()").value(1))
                .andExpect(jsonPath("$.mensajes[0].contenido").value("Incluye cables?"))
                .andExpect(jsonPath("$.tieneSiguiente").value(false));
    }

    @Test void rechazaParametrosDePaginacionInvalidos() throws Exception {
        long id = consultar();
        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tc).param("pagina", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tc).param("tamano", "101"))
                .andExpect(status().isBadRequest());
    }
    @Test void inexistentes404() throws Exception {
        mvc.perform(post("/api/conversaciones").header("Authorization", "Bearer " + tc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productoId\":999999,\"mensaje\":\"Hola\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/conversaciones/999999/mensajes").header("Authorization", "Bearer " + tv).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensaje\":\"Hola\"}")).andExpect(status().isNotFound());
    }
    @Test void clienteNoPuedeResponderComoVendedor() throws Exception {
        long id = consultar();
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).header("Authorization", "Bearer " + tc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensaje\":\"Hola\"}")).andExpect(status().isForbidden());
    }
    @Test void retiradoNoAdmiteConsultaYConfiguracionEsCompartida() throws Exception {
        producto.setEstado("RETIRADO"); productos.saveAndFlush(producto);
        mvc.perform(post("/api/conversaciones").header("Authorization", "Bearer " + tc).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("productoId", producto.getId(), "mensaje", "Hola")))).andExpect(status().isConflict());
        mvc.perform(get("/api/conversaciones/configuracion").header("Authorization", "Bearer " + tc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.longitudMaxima").value(80));
    }
    @Test void permitePreflightDelFrontendConBearer() throws Exception {
        mvc.perform(options("/api/conversaciones").header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }
}
