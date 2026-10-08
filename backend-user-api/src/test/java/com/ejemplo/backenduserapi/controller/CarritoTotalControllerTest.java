package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Carrito;
import com.ejemplo.backenduserapi.entity.ItemCarrito;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CarritoTotalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ProductoRepository productoRepository;
    @Autowired CarritoRepository carritoRepository;
    @Autowired ItemCarritoRepository itemCarritoRepository;

    @BeforeEach
    void preparar() {
        limpiar();
    }

    @AfterEach
    void finalizar() {
        limpiar();
    }

    private void limpiar() {
        itemCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    private Producto producto(String nombre, String precio) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setDescripcion("Prueba HU14");
        p.setPrecio(new BigDecimal(precio));
        p.setStock(20);
        p.setCategoria("Electronica");
        p.setEstado("DISPONIBLE");
        p.setImagenUrl("/imagenes/producto.png");
        p.setVendedorId(1L);
        return productoRepository.save(p);
    }

    private Long agregar(Carrito carrito, Producto producto, int cantidad) {
        return itemCarritoRepository.save(
                new ItemCarrito(carrito, producto, cantidad, producto.getPrecio())
        ).getId();
    }

    @Test
    void carritoVacioTieneTotalCero() throws Exception {
        mockMvc.perform(get("/api/carrito/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void sumaSubtotalesDeVariosProductos() throws Exception {
        Carrito carrito = carritoRepository.save(new Carrito(1L));
        agregar(carrito, producto("Teclado", "50.00"), 2);
        agregar(carrito, producto("Mouse", "20.00"), 3);

        mockMvc.perform(get("/api/carrito/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(160.00));
    }

    @Test
    void actualizaTotalAlCambiarCantidad() throws Exception {
        Carrito carrito = carritoRepository.save(new Carrito(1L));
        Long id = agregar(carrito, producto("Teclado", "50.00"), 2);

        mockMvc.perform(put("/api/carrito/items/" + id)
                        .contentType("application/json")
                        .content("{\"cantidad\":3}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/carrito/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(150.00));
    }

    @Test
    void actualizaTotalAlEliminarProducto() throws Exception {
        Carrito carrito = carritoRepository.save(new Carrito(1L));
        Long id = agregar(carrito, producto("Teclado", "50.00"), 2);
        agregar(carrito, producto("Mouse", "20.00"), 3);

        mockMvc.perform(delete("/api/carrito/items/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/carrito/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(60.00));
    }
}
