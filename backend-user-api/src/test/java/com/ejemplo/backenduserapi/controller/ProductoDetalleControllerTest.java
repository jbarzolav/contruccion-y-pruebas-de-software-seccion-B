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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la HU 07 - Visualizar el detalle de un producto
 *
 * Endpoints:
 *   GET /api/productos/{idProducto}  → 200 detalle completo · 404 si no existe
 *   GET /api/productos/catalogo      → 200 solo productos DISPONIBLE
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductoDetalleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @BeforeEach
    void limpiarBaseDeDatos() {
        productoRepository.deleteAll();
    }

    // Limpia también al terminar: otras clases comparten la H2 en memoria
    @AfterEach
    void limpiarAlTerminar() {
        productoRepository.deleteAll();
    }

    private Producto crearProducto(String nombre, String estado) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Descripción de " + nombre);
        producto.setPrecio(new BigDecimal("199.90"));
        producto.setStock(7);
        producto.setCategoria("Electronica");
        producto.setEstado(estado);
        producto.setImagenUrl("/imagenes/foto_producto.png");
        producto.setVendedorId(1L);

        return productoRepository.save(producto);
    }

    // 1. Detalle completo: todos los campos que consume la vista de detalle (HU 07)
    @Test
    void obtenerDetalleDeProductoExistente() throws Exception {
        Producto creado = crearProducto("Teclado Mecanico", "DISPONIBLE");

        mockMvc.perform(get("/api/productos/" + creado.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creado.getId()))
                .andExpect(jsonPath("$.nombre").value("Teclado Mecanico"))
                .andExpect(jsonPath("$.descripcion").value("Descripción de Teclado Mecanico"))
                .andExpect(jsonPath("$.precio").value(199.90))
                .andExpect(jsonPath("$.stock").value(7))
                .andExpect(jsonPath("$.categoria").value("Electronica"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.imagenUrl").value("/imagenes/foto_producto.png"))
                .andExpect(jsonPath("$.vendedorId").value(1));
    }

    // 2. Producto no existente → 404 con la forma estándar {status, error, messages}
    @Test
    void obtenerDetalleDeProductoInexistente() throws Exception {
        mockMvc.perform(get("/api/productos/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Producto no encontrado"))
                .andExpect(jsonPath("$.messages[0]").value("Producto no encontrado con id 99999"));
    }

    // 3. Producto RETIRADO → 200 con su estado: es baja lógica (HU 04), sigue en la BD
    //    y su dueño debe poder seguir viéndolo / editándolo.
    @Test
    void detalleDeProductoRetiradoSigueConsultable() throws Exception {
        Producto retirado = crearProducto("Mouse Retirado", "RETIRADO");

        mockMvc.perform(get("/api/productos/" + retirado.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(retirado.getId()))
                .andExpect(jsonPath("$.estado").value("RETIRADO"));
    }

    // 4. Catálogo: solo DISPONIBLE (misma regla que la búsqueda del HU 06);
    //    RETIRADO y AGOTADO no se muestran al público.
    @Test
    void listarCatalogoSoloProductosDisponibles() throws Exception {
        crearProducto("Laptop Disponible", "DISPONIBLE");
        crearProducto("Laptop Retirada", "RETIRADO");
        crearProducto("Laptop Agotada", "AGOTADO");

        mockMvc.perform(get("/api/productos/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Laptop Disponible"))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"));
    }
}
