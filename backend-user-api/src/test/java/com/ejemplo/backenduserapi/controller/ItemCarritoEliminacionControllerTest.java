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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ItemCarritoEliminacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private ItemCarritoRepository itemCarritoRepository;

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

    private Producto crearProducto(String nombre) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para pruebas HU13");
        producto.setPrecio(new BigDecimal("50.00"));
        producto.setStock(10);
        producto.setCategoria("Electronica");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(1L);
        return productoRepository.save(producto);
    }

    private Long crearItem(Carrito carrito, Producto producto) {
        ItemCarrito item = new ItemCarrito(
                carrito, producto, 2, producto.getPrecio());
        return itemCarritoRepository.save(item).getId();
    }

    @Test
    void eliminarItemExistente() throws Exception {
        Producto producto = crearProducto("Teclado");
        Carrito carrito = carritoRepository.save(new Carrito(1L));
        Long idItem = crearItem(carrito, producto);

        mockMvc.perform(delete("/api/carrito/items/" + idItem))
                .andExpect(status().isNoContent());

        assertFalse(itemCarritoRepository.existsById(idItem));
        assertTrue(productoRepository.existsById(producto.getId()));
    }

    @Test
    void eliminarItemInexistente() throws Exception {
        mockMvc.perform(delete("/api/carrito/items/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error")
                        .value("Item de carrito no encontrado"));
    }

    @Test
    void eliminarUnItemConservaLosDemas() throws Exception {
        Producto producto1 = crearProducto("Teclado");
        Producto producto2 = crearProducto("Mouse");
        Carrito carrito = carritoRepository.save(new Carrito(1L));

        Long idItem1 = crearItem(carrito, producto1);
        Long idItem2 = crearItem(carrito, producto2);

        mockMvc.perform(delete("/api/carrito/items/" + idItem1))
                .andExpect(status().isNoContent());

        assertFalse(itemCarritoRepository.existsById(idItem1));
        assertTrue(itemCarritoRepository.existsById(idItem2));
        assertTrue(carritoRepository.existsById(carrito.getId()));
    }
}
