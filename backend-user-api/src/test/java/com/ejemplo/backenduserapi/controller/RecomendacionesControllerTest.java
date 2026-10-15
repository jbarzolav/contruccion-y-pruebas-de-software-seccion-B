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

/**
 * HU 20 - GET /api/recomendaciones?termino={texto}
 *
 * Prueba el endpoint real sobre H2: coincidencias por nombre y por
 * categoría, término vacío/sin coincidencias, sin duplicados y
 * exclusión de productos no disponibles.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecomendacionesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @BeforeEach
    void limpiarBaseDeDatos() {
        productoRepository.deleteAll();
    }

    private Long crearProducto(String nombre, String categoria, String estado) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para prueba de recomendaciones HU 20");
        producto.setPrecio(new BigDecimal("50.00"));
        producto.setStock(5);
        producto.setCategoria(categoria);
        producto.setEstado(estado);
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(1L);

        return productoRepository.save(producto).getId();
    }

    // ============================================================
    // COINCIDENCIAS
    // ============================================================
    @Test
    void recomendarPorCoincidenciaDeNombre() throws Exception {
        Long id = crearProducto("Arduino Uno", "Microcontroladores", "DISPONIBLE");

        mockMvc.perform(get("/api/recomendaciones")
                        .param("termino", "arduino"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].nombre").value("Arduino Uno"))
                .andExpect(jsonPath("$[0].precio").value(50.00))
                .andExpect(jsonPath("$[0].stock").value(5))
                .andExpect(jsonPath("$[0].categoria").value("Microcontroladores"))
                .andExpect(jsonPath("$[0].imagenUrl").value("/imagenes/producto.png"))
                // El DTO de resumen no expone estado (decisión HU 20)
                .andExpect(jsonPath("$[0].estado").doesNotExist());
    }

    @Test
    void recomendarPorCoincidenciaDeCategoria() throws Exception {
        crearProducto("Placa de desarrollo", "Microcontroladores", "DISPONIBLE");

        mockMvc.perform(get("/api/recomendaciones")
                        .param("termino", "microcontroladores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Placa de desarrollo"));
    }

    @Test
    void recomendarSinCoincidenciasDevuelveVacio() throws Exception {
        crearProducto("Arduino Uno", "Microcontroladores", "DISPONIBLE");

        mockMvc.perform(get("/api/recomendaciones")
                        .param("termino", "raspberry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ============================================================
    // TÉRMINO VACÍO / AUSENTE -> 200 con lista vacía, nunca 500
    // ============================================================
    @Test
    void recomendarConTerminoVacioDevuelveVacio() throws Exception {
        crearProducto("Arduino Uno", "Microcontroladores", "DISPONIBLE");

        mockMvc.perform(get("/api/recomendaciones")
                        .param("termino", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void recomendarSinParametroTerminoDevuelveVacio() throws Exception {
        crearProducto("Arduino Uno", "Microcontroladores", "DISPONIBLE");

        mockMvc.perform(get("/api/recomendaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ============================================================
    // DUPLICADOS: coincidencia simultánea por nombre y categoría
    // ============================================================
    @Test
    void evitarDuplicadosCuandoCoincideNombreYCategoria() throws Exception {
        Long idArduino = crearProducto("Kit Arduino", "Arduino", "DISPONIBLE");
        Long idProtoboard = crearProducto("Protoboard", "Arduino", "DISPONIBLE");

        mockMvc.perform(get("/api/recomendaciones")
                        .param("termino", "arduino"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(idArduino))
                .andExpect(jsonPath("$[1].id").value(idProtoboard));
    }

    // ============================================================
    // DISPONIBILIDAD: solo DISPONIBLE (se excluyen RETIRADO,
    // INACTIVO y AGOTADO)
    // ============================================================
    @Test
    void excluirProductosNoDisponiblesDeLasRecomendaciones() throws Exception {
        crearProducto("Mouse optico", "Accesorios", "DISPONIBLE");
        crearProducto("Mouse gamer", "Accesorios", "RETIRADO");
        crearProducto("Mouse vertical", "Accesorios", "INACTIVO");
        crearProducto("Mousepad XL", "Accesorios", "AGOTADO");

        mockMvc.perform(get("/api/recomendaciones")
                        .param("termino", "mouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Mouse optico"));
    }
}
