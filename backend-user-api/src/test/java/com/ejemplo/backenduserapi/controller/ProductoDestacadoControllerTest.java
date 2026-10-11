package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 19 - Destacar publicaciones de vendedor Premium.
 * PATCH /api/productos/{idProducto}/destacado -> activar o desactivar el
 * destacado; solo cambia el indicador, sin tocar los demás datos.
 *
 * Pruebas exigidas por el documento de la HU 19 (tarea 6):
 *  - vendedor premium ............ 200 (activa y desactiva)
 *  - vendedor sin premium ........ 403 con cuerpo semántico
 *  - producto ajeno .............. 403 (pertenece a otro vendedor)
 *  - producto inexistente ........ 404
 *
 * El estado del plan Premium se verifica en la BD (tabla vendedores_plan,
 * sembrada con el vendedor 1 ACTIVO por convención hasta que exista JWT).
 * La suite corre offline sobre H2 (sin servicios externos).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductoDestacadoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @BeforeEach
    void preparar() {
        productoRepository.deleteAll();
    }

    @AfterEach
    void finalizar() {
        productoRepository.deleteAll();
    }

    private Producto crearProducto(String nombre, String categoria, String precio,
                                   int stock, String estado, Long vendedorId) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para pruebas HU19");
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(stock);
        producto.setCategoria(categoria);
        producto.setEstado(estado);
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(vendedorId);
        return productoRepository.save(producto);
    }

    // ---------------------------------------------------------------
    // 1) Vendedor PREMIUM -> 200 y el indicador queda activo
    // ---------------------------------------------------------------
    @Test
    void vendedorPremiumActivaElDestacado() throws Exception {
        Producto producto = crearProducto(
                "Notebook Lenovo", "Computadoras", "2500.00", 5, "DISPONIBLE", 1L);

        mockMvc.perform(patch("/api/productos/" + producto.getId() + "/destacado")
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destacado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(producto.getId()))
                .andExpect(jsonPath("$.nombre").value("Notebook Lenovo"))
                .andExpect(jsonPath("$.esDestacado").value(true))
                .andExpect(jsonPath("$.fechaDestacado").exists());
    }

    // ---------------------------------------------------------------
    // 2) Desactivar -> 200 y SOLO cambia el indicador (tarea 3)
    // ---------------------------------------------------------------
    @Test
    void vendedorPremiumDesactivaElDestacadoSinTocarLosDemasDatos() throws Exception {
        Producto producto = crearProducto(
                "Mouse Logitech", "Accesorios", "89.90", 10, "DISPONIBLE", 1L);

        // Activar primero.
        mockMvc.perform(patch("/api/productos/" + producto.getId() + "/destacado")
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destacado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDestacado").value(true));

        // Desactivar: cambia SOLO el indicador.
        mockMvc.perform(patch("/api/productos/" + producto.getId() + "/destacado")
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destacado\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDestacado").value(false))
                .andExpect(jsonPath("$.fechaDestacado").isEmpty())
                .andExpect(jsonPath("$.nombre").value("Mouse Logitech"))
                .andExpect(jsonPath("$.precio").value(89.90))
                .andExpect(jsonPath("$.stock").value(10))
                .andExpect(jsonPath("$.categoria").value("Accesorios"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }

    // ---------------------------------------------------------------
    // 3) Sin cuerpo -> alterna el estado actual (activar/desactivar)
    // ---------------------------------------------------------------
    @Test
    void sinCuerpoAlteraElEstadoDelDestacado() throws Exception {
        Producto producto = crearProducto(
                "Teclado", "Accesorios", "80.00", 6, "DISPONIBLE", 1L);
        String ruta = "/api/productos/" + producto.getId() + "/destacado";

        mockMvc.perform(patch(ruta).param("vendedorId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDestacado").value(true));

        mockMvc.perform(patch(ruta).param("vendedorId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDestacado").value(false));
    }

    // ---------------------------------------------------------------
    // 4) Vendedor SIN plan Premium -> 403 y la publicación queda intacta
    // ---------------------------------------------------------------
    @Test
    void vendedorSinPlanPremiumRecibe403() throws Exception {
        Producto producto = crearProducto(
                "Auriculares", "Accesorios", "149.90", 3, "DISPONIBLE", 7L);

        mockMvc.perform(patch("/api/productos/" + producto.getId() + "/destacado")
                        .param("vendedorId", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destacado\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Beneficio premium no disponible"))
                .andExpect(jsonPath("$.messages[0]").value(
                        "El vendedor no cuenta con el plan Premium para destacar publicaciones"));

        // La publicación NO queda destacada.
        mockMvc.perform(get("/api/productos/" + producto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDestacado").value(false));
    }

    // ---------------------------------------------------------------
    // 5) Producto AJENO (de otro vendedor) -> 403
    // ---------------------------------------------------------------
    @Test
    void productoAjenoDevuelve403() throws Exception {
        Producto producto = crearProducto(
                "Monitor Dell", "Computadoras", "899.90", 2, "DISPONIBLE", 7L);

        // El vendedor 1 (premium) intenta destacar el producto del vendedor 7.
        mockMvc.perform(patch("/api/productos/" + producto.getId() + "/destacado")
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destacado\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Acceso denegado"))
                .andExpect(jsonPath("$.messages[0]").value(
                        "El producto no pertenece al vendedor"));

        mockMvc.perform(get("/api/productos/" + producto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDestacado").value(false));
    }

    // ---------------------------------------------------------------
    // 6) Producto INEXISTENTE -> 404
    // ---------------------------------------------------------------
    @Test
    void productoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(patch("/api/productos/999999/destacado")
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destacado\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Producto no encontrado"));
    }

    // ---------------------------------------------------------------
    // 7) El catálogo ordena por defecto los destacados al inicio
    //    (comportamiento extra pedido en la planificación del Sprint 1)
    // ---------------------------------------------------------------
    @Test
    void catalogoOrdenaLosDestacadosPrimero() throws Exception {
        crearProducto("Mouse", "Accesorios", "50.00", 4, "DISPONIBLE", 1L);
        Producto destacado = crearProducto(
                "Notebook Lenovo", "Computadoras", "2500.00", 5, "DISPONIBLE", 1L);
        crearProducto("Teclado", "Accesorios", "80.00", 6, "DISPONIBLE", 1L);

        mockMvc.perform(patch("/api/productos/" + destacado.getId() + "/destacado")
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destacado\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/productos/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Notebook Lenovo"))
                .andExpect(jsonPath("$[0].esDestacado").value(true));
    }
}
