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
class ProductoOrdenamientoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @BeforeEach
    void limpiarBaseDeDatos() {
        productoRepository.deleteAll();
    }

    private void crearProducto(String nombre, String precio) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para prueba de ordenamiento");
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(5);
        producto.setCategoria("Electronica");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(1L);

        productoRepository.save(producto);
    }

    @Test
    void ordenarPorPrecioMenorAMayor() throws Exception {
        crearProducto("Sensor C", "80.00");
        crearProducto("Sensor A", "20.00");
        crearProducto("Sensor B", "50.00");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "Sensor")
                        .param("sort", "precio_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].precio").value(20.00))
                .andExpect(jsonPath("$[1].precio").value(50.00))
                .andExpect(jsonPath("$[2].precio").value(80.00));
    }

    @Test
    void ordenarPorPrecioMayorAMenor() throws Exception {
        crearProducto("Sensor C", "80.00");
        crearProducto("Sensor A", "20.00");
        crearProducto("Sensor B", "50.00");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "Sensor")
                        .param("sort", "precio_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].precio").value(80.00))
                .andExpect(jsonPath("$[1].precio").value(50.00))
                .andExpect(jsonPath("$[2].precio").value(20.00));
    }

    @Test
    void ordenarPorNombreAZ() throws Exception {
        crearProducto("Sensor Zeta", "50.00");
        crearProducto("Sensor Alfa", "50.00");
        crearProducto("Sensor Medio", "50.00");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "Sensor")
                        .param("sort", "nombre_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Sensor Alfa"))
                .andExpect(jsonPath("$[1].nombre").value("Sensor Medio"))
                .andExpect(jsonPath("$[2].nombre").value("Sensor Zeta"));
    }

    @Test
    void ordenarPorNombreZA() throws Exception {
        crearProducto("Sensor Zeta", "50.00");
        crearProducto("Sensor Alfa", "50.00");
        crearProducto("Sensor Medio", "50.00");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "Sensor")
                        .param("sort", "nombre_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Sensor Zeta"))
                .andExpect(jsonPath("$[1].nombre").value("Sensor Medio"))
                .andExpect(jsonPath("$[2].nombre").value("Sensor Alfa"));
    }

    @Test
    void rechazarCriterioDeOrdenamientoNoPermitido() throws Exception {
        crearProducto("Sensor Ultrasonico", "50.00");

        mockMvc.perform(get("/api/productos")
                        .param("nombre", "Sensor")
                        .param("sort", "criterio_invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error")
                        .value("Criterio de ordenamiento inválido"));
    }
}