package com.ejemplo.backenduserapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ejemplo.backenduserapi.dto.ProductoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas unitarias de la HU 01 - Registro de productos
 * Endpoint: POST /api/productos
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ProductoRequest productoValido() {
        ProductoRequest request = new ProductoRequest();
        request.setNombre("Laptop Lenovo IdeaPad");
        request.setDescripcion("Laptop con 16GB de RAM y 512GB SSD");
        request.setPrecio(new BigDecimal("3500.00"));
        request.setStock(10);
        request.setCategoria("Computadoras");
        request.setEstado("DISPONIBLE");
        request.setImagenUrl("https://example.com/laptop.png");
        request.setVendedorId(1L);
        return request;
    }

    // 1. Registro exitoso
    @Test
    void registrarProductoExitosamente() throws Exception {
        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productoValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").value("Laptop Lenovo IdeaPad"))
                .andExpect(jsonPath("$.precio").value(3500.00))
                .andExpect(jsonPath("$.stock").value(10));
    }

    // 2. Rechazo por precio <= 0
    @Test
    void rechazarProductoConPrecioCero() throws Exception {
        ProductoRequest request = productoValido();
        request.setPrecio(BigDecimal.ZERO);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages", hasItem("El precio debe ser mayor a 0")));
    }

    @Test
    void rechazarProductoConPrecioNegativo() throws Exception {
        ProductoRequest request = productoValido();
        request.setPrecio(new BigDecimal("-100.50"));

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages", hasItem("El precio debe ser mayor a 0")));
    }

    // 3. Rechazo por stock < 0
    @Test
    void rechazarProductoConStockNegativo() throws Exception {
        ProductoRequest request = productoValido();
        request.setStock(-5);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages", hasItem("El stock no puede ser negativo")));
    }

    // 4. Campos obligatorios vacíos
    @Test
    void rechazarProductoConCamposObligatoriosVacios() throws Exception {
        ProductoRequest request = productoValido();
        request.setNombre("");
        request.setDescripcion("");
        request.setCategoria("");
        request.setEstado("");
        request.setImagenUrl("");
        request.setPrecio(null);
        request.setStock(null);
        request.setVendedorId(null);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages", hasItem("El nombre es obligatorio")))
                .andExpect(jsonPath("$.messages", hasItem("La descripción es obligatoria")))
                .andExpect(jsonPath("$.messages", hasItem("El precio es obligatorio")))
                .andExpect(jsonPath("$.messages", hasItem("El stock es obligatorio")));
    }
}
