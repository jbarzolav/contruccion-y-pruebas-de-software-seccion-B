package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
class ProductoDisponibilidadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @BeforeEach
    void limpiarBaseDeDatos() {
        productoRepository.deleteAll();
    }

    private Producto crearProducto(Integer stock, String estado) {
        Producto producto = new Producto();
        producto.setNombre("Arduino Uno");
        producto.setDescripcion("Producto para prueba de disponibilidad");
        producto.setPrecio(new BigDecimal("80.00"));
        producto.setStock(stock);
        producto.setCategoria("Electronica");
        producto.setEstado(estado);
        producto.setImagenUrl("/imagenes/arduino.png");
        producto.setVendedorId(1L);

        return productoRepository.save(producto);
    }

    @Test
    void productoConStockPositivoEstaDisponible() throws Exception {
        Producto producto = crearProducto(5, "DISPONIBLE");

        mockMvc.perform(get("/api/productos/{idProducto}/disponibilidad", producto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idProducto").value(producto.getId()))
                .andExpect(jsonPath("$.stock").value(5))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    void productoConStockCeroNoEstaDisponible() throws Exception {
        Producto producto = crearProducto(0, "DISPONIBLE");

        mockMvc.perform(get("/api/productos/{idProducto}/disponibilidad", producto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    void productoRetiradoNoEstaDisponible() throws Exception {
        Producto producto = crearProducto(5, "RETIRADO");

        mockMvc.perform(get("/api/productos/{idProducto}/disponibilidad", producto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETIRADO"))
                .andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    void productoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/productos/{idProducto}/disponibilidad", 999999L))
                .andExpect(status().isNotFound());
    }
}
