package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 19 - Destacar publicaciones de vendedor Premium.
 * PATCH /api/productos/{id}/destacar
 *  - 200 con el producto actualizado (esDestacado + fechaDestacado).
 *  - 403 con cuerpo semántico {status, error, messages} si el vendedor
 *    no cuenta con el beneficio Premium.
 *  - Catálogo: los destacados encabezan el listado (orden por defecto).
 *
 * Registro de Premium (PlanPremiumService): sin autenticación real aún,
 * el vendedor fijo 1 nace Premium; el vendedor 7 no lo está.
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
    // 1) Vendedor Premium destaca su publicación -> 200 + marcadores
    // ---------------------------------------------------------------
    @Test
    void vendedorPremiumDestacaSuPublicacion() throws Exception {
        Producto producto = crearProducto(
                "Notebook Lenovo", "Computadoras", "2500.00", 5, "DISPONIBLE", 1L);

        mockMvc.perform(patch("/api/productos/" + producto.getId() + "/destacar")
                        .param("vendedorId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(producto.getId()))
                .andExpect(jsonPath("$.nombre").value("Notebook Lenovo"))
                .andExpect(jsonPath("$.esDestacado").value(true))
                .andExpect(jsonPath("$.fechaDestacado").exists());
    }

    // ---------------------------------------------------------------
    // 2) Vendedor sin plan Premium -> 403 y la publicación queda intacta
    // ---------------------------------------------------------------
    @Test
    void vendedorSinPlanPremiumRecibe403() throws Exception {
        Producto producto = crearProducto(
                "Mouse Logitech", "Accesorios", "89.90", 10, "DISPONIBLE", 7L);

        mockMvc.perform(patch("/api/productos/" + producto.getId() + "/destacar")
                        .param("vendedorId", "7"))
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
    // 3) El catálogo ordena por defecto los destacados al inicio
    // ---------------------------------------------------------------
    @Test
    void catalogoOrdenaLosDestacadosPrimero() throws Exception {
        crearProducto("Mouse", "Accesorios", "50.00", 4, "DISPONIBLE", 1L);
        Producto destacado = crearProducto(
                "Notebook Lenovo", "Computadoras", "2500.00", 5, "DISPONIBLE", 1L);
        crearProducto("Teclado", "Accesorios", "80.00", 6, "DISPONIBLE", 1L);

        mockMvc.perform(patch("/api/productos/" + destacado.getId() + "/destacar")
                        .param("vendedorId", "1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/productos/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Notebook Lenovo"))
                .andExpect(jsonPath("$[0].esDestacado").value(true));
    }
}
