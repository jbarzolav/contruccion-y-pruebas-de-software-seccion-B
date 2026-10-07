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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la HU 12 - Modificar la cantidad de un producto en el carrito
 *
 * Endpoint:
 *   PUT /api/carrito/items/{idItemCarrito}  Body: { cantidad }
 *     → 200 ítem actualizado con el subtotal RECALCULADO por el servidor
 *     → 400 si la cantidad es < 1 o supera el stock actual de la BD
 *     → 404 si el ítem no existe
 */
@SpringBootTest
@AutoConfigureMockMvc
class ItemCarritoModificacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private ItemCarritoRepository itemCarritoRepository;

    @BeforeEach
    void limpiarBaseDeDatos() {
        itemCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    // Limpia también al terminar: otras clases comparten la H2 en memoria
    // y no deben chocar con las foraneas de items_carrito.
    @AfterEach
    void limpiarAlTerminar() {
        itemCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    private Producto crearProducto(Integer stock) {
        Producto producto = new Producto();
        producto.setNombre("Teclado Compacto");
        producto.setDescripcion("Teclado para pruebas de cantidad");
        producto.setPrecio(new BigDecimal("199.90"));
        producto.setStock(stock);
        producto.setCategoria("Electronica");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("/imagenes/teclado.png");
        producto.setVendedorId(1L);

        return productoRepository.save(producto);
    }

    /**
     * Crea el carrito y el ítem directamente en la BD (snapshot de precio incluido),
     * para poder probar el PUT de la HU 12 de forma aislada.
     */
    private Long crearItem(Producto producto, Integer cantidad) {
        Carrito carrito = carritoRepository.save(new Carrito(1L));
        ItemCarrito item = new ItemCarrito(carrito, producto, cantidad, producto.getPrecio());
        return itemCarritoRepository.save(item).getId();
    }

    // 1. Modificación válida → 200 con el subtotal recalculado por el servidor
    @Test
    void modificarCantidadValidaRecalculaSubtotal() throws Exception {
        Producto producto = crearProducto(10);
        Long idItem = crearItem(producto, 1);

        mockMvc.perform(put("/api/carrito/items/" + idItem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idItem))
                .andExpect(jsonPath("$.cantidad").value(3))
                .andExpect(jsonPath("$.precioUnitario").value(199.90))
                // 3 * 199.90 = 599.70 → lo calcula SIEMPRE el backend
                .andExpect(jsonPath("$.subtotal").value(599.70));
    }

    // 2. Ajuste al límite exacto del stock → 200 (cantidad == stock vale)
    @Test
    void modificarCantidadHastaElLimiteDeStock() throws Exception {
        Producto producto = crearProducto(3);
        Long idItem = crearItem(producto, 1);

        mockMvc.perform(put("/api/carrito/items/" + idItem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(3))
                .andExpect(jsonPath("$.subtotal").value(599.70));
    }

    // 3. Intento de bajar a cero → 400 (validación Jakarta en el DTO)
    @Test
    void modificarCantidadACero() throws Exception {
        Producto producto = crearProducto(10);
        Long idItem = crearItem(producto, 2);

        mockMvc.perform(put("/api/carrito/items/" + idItem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages[0]").value("La cantidad debe ser al menos 1"));
    }

    // 4. Intento de superar el stock ACTUAL de la BD → 400 con regla de negocio
    @Test
    void modificarCantidadSuperiorAlStock() throws Exception {
        Producto producto = crearProducto(4);
        Long idItem = crearItem(producto, 2);

        mockMvc.perform(put("/api/carrito/items/" + idItem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Item de carrito inválido"))
                .andExpect(jsonPath("$.messages[0]")
                        .value("La cantidad solicitada (9) supera el stock disponible (4)"));
    }

    // 5. Ítem inexistente → 404 con la forma estándar {status, error, messages}
    @Test
    void modificarItemInexistente() throws Exception {
        mockMvc.perform(put("/api/carrito/items/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Item de carrito no encontrado"))
                .andExpect(jsonPath("$.messages[0]").value("Ítem de carrito no encontrado con id 99999"));
    }
}
