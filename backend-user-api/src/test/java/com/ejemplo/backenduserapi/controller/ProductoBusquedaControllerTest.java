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
class ProductoBusquedaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @BeforeEach
    void limpiarBaseDeDatos() {
        productoRepository.deleteAll();
    }

    private void crearProducto(String nombre, String estado) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para prueba de búsqueda");
        producto.setPrecio(new BigDecimal("50.00"));
        producto.setStock(5);
        producto.setCategoria("Electronica");
        producto.setEstado(estado);
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(1L);

        productoRepository.save(producto);
    }

    @Test
    void buscarProductoPorNombreExistente() throws Exception {
        crearProducto("Protoboard 830 puntos", "DISPONIBLE");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "proto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Protoboard 830 puntos"))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"));
    }

    @Test
    void buscarProductoPorNombreInexistente() throws Exception {
        crearProducto("Arduino Uno", "DISPONIBLE");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "Raspberry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void buscarConTextoVacio() throws Exception {
        crearProducto("Sensor Ultrasonico", "DISPONIBLE");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void excluirProductoRetiradoDeLaBusqueda() throws Exception {
        crearProducto("Protoboard disponible", "DISPONIBLE");
        crearProducto("Protoboard retirado", "RETIRADO");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "Protoboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Protoboard disponible"))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"));
    }
}
