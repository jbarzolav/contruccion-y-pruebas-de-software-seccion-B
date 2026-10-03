package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ProductoRequest;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 03 - Edición de productos
 * Endpoint: PUT /api/productos/{idProducto}?vendedorId=1
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductoUpdateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long crearProductoDePrueba(Long vendedorId) {
        Producto producto = new Producto();
        producto.setNombre("Mouse Gamer");
        producto.setDescripcion("Mouse con 8 botones");
        producto.setPrecio(new BigDecimal("150.00"));
        producto.setStock(4);
        producto.setCategoria("Accesorios");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("https://via.placeholder.com/300");
        producto.setVendedorId(vendedorId);

        return productoRepository.save(producto).getId();
    }

    private ProductoRequest cuerpoDePrueba() {
        ProductoRequest request = new ProductoRequest();
        request.setNombre("Mouse Gamer Pro");
        request.setDescripcion("Mouse con 12 botones y RGB");
        request.setPrecio(new BigDecimal("199.90"));
        request.setStock(12);
        request.setCategoria("Accesorios");
        request.setEstado("DISPONIBLE");
        request.setImagenUrl("https://via.placeholder.com/300");
        request.setVendedorId(1L);
        return request;
    }

    // Edición exitosa -> 200
    @Test
    void editarProductoExitosamente() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        mockMvc.perform(put("/api/productos/{idProducto}", idProducto)
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoDePrueba())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idProducto))
                .andExpect(jsonPath("$.nombre").value("Mouse Gamer Pro"))
                .andExpect(jsonPath("$.precio").value(199.90))
                .andExpect(jsonPath("$.stock").value(12));
    }

    // Producto de otro vendedor -> 403
    @Test
    void rechazarEdicionDeProductoDeOtroVendedor() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        mockMvc.perform(put("/api/productos/{idProducto}", idProducto)
                        .param("vendedorId", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoDePrueba())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // Producto inexistente -> 404
    @Test
    void rechazarEdicionDeProductoInexistente() throws Exception {
        mockMvc.perform(put("/api/productos/{idProducto}", 999999L)
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoDePrueba())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // Datos inválidos -> 400
    @Test
    void rechazarEdicionConDatosInvalidos() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        ProductoRequest invalido = cuerpoDePrueba();
        invalido.setPrecio(new BigDecimal("-10"));
        invalido.setStock(-3);
        invalido.setNombre("");

        mockMvc.perform(put("/api/productos/{idProducto}", idProducto)
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
