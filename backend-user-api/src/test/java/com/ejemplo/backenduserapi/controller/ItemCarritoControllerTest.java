package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.CarritoRepository;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la HU 11 - Agregar productos al carrito
 *
 * Endpoint:
 *   POST /api/carrito/items  Body: { productoId, cantidad }
 *     → 201 ítem creado con subtotal calculado por el servidor
 *     → 400 si la cantidad es < 1 o supera el stock disponible
 *     → 404 si el producto no existe
 */
@SpringBootTest
@AutoConfigureMockMvc
class ItemCarritoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private ItemCarritoRepository itemCarritoRepository;

    @BeforeEach
    void limpiarBaseDeDatos() {
        itemCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    // Limpia también al terminar: otras clases comparten la H2 en memoria
    // y no deben chocar con las foraneas de items_carrito.
    @AfterEach
    void limpiarAlTerminar() {
        itemCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    private Producto crearProducto(Integer stock) {
        Producto producto = new Producto();
        producto.setNombre("Notebook Delgado");
        producto.setDescripcion("Notebook para pruebas del carrito");
        producto.setPrecio(new BigDecimal("199.90"));
        producto.setStock(stock);
        producto.setCategoria("Electronica");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("/imagenes/notebook.png");
        producto.setVendedorId(1L);

        return productoRepository.save(producto);
    }

    private String cuerpo(long productoId, int cantidad) {
        return String.format("{\"productoId\":%d,\"cantidad\":%d}", productoId, cantidad);
    }

    // 1. Agregar con éxito → 201 y el ítem con subtotal calculado por el servidor
    @Test
    void agregarItemAlCarritoConExito() throws Exception {
        Producto producto = crearProducto(5);

        mockMvc.perform(post("/api/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(producto.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productoId").value(producto.getId()))
                .andExpect(jsonPath("$.nombreProducto").value("Notebook Delgado"))
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.precioUnitario").value(199.90))
                .andExpect(jsonPath("$.subtotal").value(399.80))
                .andExpect(jsonPath("$.stockDisponible").value(5));
    }

    // 2. Cantidad cero → 400 (validación Jakarta en el DTO)
    @Test
    void agregarItemConCantidadCero() throws Exception {
        Producto producto = crearProducto(5);

        mockMvc.perform(post("/api/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(producto.getId(), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validación fallida"))
                .andExpect(jsonPath("$.messages[0]").value("La cantidad debe ser al menos 1"));
    }

    // 3. Cantidad negativa → 400
    @Test
    void agregarItemConCantidadNegativa() throws Exception {
        Producto producto = crearProducto(5);

        mockMvc.perform(post("/api/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(producto.getId(), -3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // 4. Cantidad superior al stock de la BD → 400 con regla de negocio
    @Test
    void agregarItemConCantidadSuperiorAlStock() throws Exception {
        Producto producto = crearProducto(3);

        mockMvc.perform(post("/api/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(producto.getId(), 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Item de carrito inválido"))
                .andExpect(jsonPath("$.messages[0]")
                        .value("La cantidad solicitada (5) supera el stock disponible (3)"));
    }

    // 5. Producto inexistente → 404 con la forma estándar {status, error, messages}
    @Test
    void agregarProductoInexistenteAlCarrito() throws Exception {
        mockMvc.perform(post("/api/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(99999, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Producto no encontrado"));
    }
}
