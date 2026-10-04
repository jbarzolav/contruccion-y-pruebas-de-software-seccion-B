package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductoMisProductosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    private Long crearProductoDePrueba(Long vendedorId, String nombre) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto de prueba");
        producto.setPrecio(new BigDecimal("50.00"));
        producto.setStock(5);
        producto.setCategoria("Electronica");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("https://via.placeholder.com/300");
        producto.setVendedorId(vendedorId);

        return productoRepository.save(producto).getId();
    }

    @Test
    void obtenerProductosDelVendedor() throws Exception {
        crearProductoDePrueba(1L, "Protoboard");

        mockMvc.perform(get("/api/vendedores/me/productos")
                        .param("vendedorId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Protoboard"))
                .andExpect(jsonPath("$[0].vendedorId").value(1));
    }

    @Test
    void obtenerListaVaciaCuandoVendedorNoTieneProductos() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/productos")
                        .param("vendedorId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void filtrarProductosPorVendedor() throws Exception {
        crearProductoDePrueba(10L, "Arduino Uno");
        crearProductoDePrueba(20L, "Sensor Ultrasonico");

        mockMvc.perform(get("/api/vendedores/me/productos")
                        .param("vendedorId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Arduino Uno"))
                .andExpect(jsonPath("$[0].vendedorId").value(10))
                .andExpect(jsonPath("$.length()").value(1));
    }
}