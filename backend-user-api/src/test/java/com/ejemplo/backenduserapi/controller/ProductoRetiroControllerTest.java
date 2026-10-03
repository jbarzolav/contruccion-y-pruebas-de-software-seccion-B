package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
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
 * HU 04 - Retirar una publicación (baja lógica)
 * Endpoint: PATCH /api/productos/{idProducto}/estado?vendedorId=1
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductoRetiroControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    private Long crearProductoDePrueba(Long vendedorId) {
        Producto producto = new Producto();
        producto.setNombre("Audífonos Bluetooth");
        producto.setDescripcion("Cancelación de ruido");
        producto.setPrecio(new BigDecimal("180.00"));
        producto.setStock(3);
        producto.setCategoria("Audio");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("https://via.placeholder.com/300");
        producto.setVendedorId(vendedorId);

        return productoRepository.save(producto).getId();
    }

    // a) Retiro correcto de publicación propia -> 200 (baja lógica)
    @Test
    void retirarPublicacionPropia() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        mockMvc.perform(patch("/api/productos/{idProducto}/estado", idProducto)
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RETIRADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idProducto))
                .andExpect(jsonPath("$.estado").value("RETIRADO"));

        // Baja lógica: el producto NO se borra, sigue consultable en la BD
        mockMvc.perform(get("/api/productos/{idProducto}", idProducto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETIRADO"));
    }

    // b) Producto inexistente -> 404
    @Test
    void rechazarRetiroDeProductoInexistente() throws Exception {
        mockMvc.perform(patch("/api/productos/{idProducto}/estado", 999999L)
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RETIRADO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // c) Producto de otro vendedor -> 403
    @Test
    void rechazarRetiroDeProductoDeOtroVendedor() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        mockMvc.perform(patch("/api/productos/{idProducto}/estado", idProducto)
                        .param("vendedorId", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RETIRADO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // Extra: sin body se asigna RETIRADO por defecto
    @Test
    void retirarSinBodyAsignaRetiradoPorDefecto() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        mockMvc.perform(patch("/api/productos/{idProducto}/estado", idProducto)
                        .param("vendedorId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETIRADO"));
    }

    // Extra: estado no permitido -> 400
    @Test
    void rechazarEstadoNoPermitido() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        mockMvc.perform(patch("/api/productos/{idProducto}/estado", idProducto)
                        .param("vendedorId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"VENDIDO-AL-MAYOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
