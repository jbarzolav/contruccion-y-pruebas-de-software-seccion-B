package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Carrito;
import com.ejemplo.backenduserapi.entity.ItemCarrito;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.CarritoRepository;
import com.ejemplo.backenduserapi.repository.DetallePedidoRepository;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 15 - "Confirmar mi compra": POST /api/pedidos
 * pedido exitoso · carrito vacío · stock insuficiente · fallo de validación.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private ItemCarritoRepository itemCarritoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    @BeforeEach
    void preparar() {
        limpiar();
    }

    @AfterEach
    void finalizar() {
        limpiar();
    }

    /** Orden por claves foráneas: detalles → pedidos → items → carritos → productos. */
    private void limpiar() {
        detallePedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        itemCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    private Producto crearProducto(String nombre, String precio, int stock) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para pruebas HU15");
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(stock);
        producto.setCategoria("Electronica");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(1L);
        return productoRepository.save(producto);
    }

    private Long agregarAlCarrito(Producto producto, int cantidad) {
        Carrito carrito = carritoRepository.findByClienteId(1L)
                .orElseGet(() -> carritoRepository.save(new Carrito(1L)));
        return itemCarritoRepository.save(
                new ItemCarrito(carrito, producto, cantidad, producto.getPrecio())
        ).getId();
    }

    // ---------------------------------------------------------------
    // 1) Pedido exitoso: 201 + total + descuento de stock + carrito vaciado
    // ---------------------------------------------------------------
    @Test
    void pedidoExitosoDescuentaStockYVaciaElCarrito() throws Exception {
        Producto notebook = crearProducto("Notebook Lenovo", "199.90", 5);
        agregarAlCarrito(notebook, 2);

        mockMvc.perform(post("/api/pedidos"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPedido").isNumber())
                .andExpect(jsonPath("$.total").value(399.80))
                .andExpect(jsonPath("$.detalles.length()").value(1))
                .andExpect(jsonPath("$.detalles[0].nombreProducto").value("Notebook Lenovo"))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(2))
                .andExpect(jsonPath("$.detalles[0].subtotal").value(399.80));

        // Stock descontado: 5 - 2 = 3
        assertEquals(3, productoRepository.findById(notebook.getId()).orElseThrow().getStock());

        // Carrito vaciado: sin ítems y sin carrito del cliente 1
        assertTrue(itemCarritoRepository.findAll().isEmpty());
        assertTrue(carritoRepository.findByClienteId(1L).isEmpty());
    }

    // ---------------------------------------------------------------
    // 2) Carrito vacío -> 400
    // ---------------------------------------------------------------
    @Test
    void pedidoConCarritoVacioDevuelve400() throws Exception {
        mockMvc.perform(post("/api/pedidos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Pedido inválido"))
                .andExpect(jsonPath("$.messages[0]").value("El carrito está vacío"));
    }

    // ---------------------------------------------------------------
    // 3) Stock insuficiente -> 400 y NO se descuenta nada
    // ---------------------------------------------------------------
    @Test
    void pedidoConStockInsuficienteDevuelve400YNoseDescuenta() throws Exception {
        Producto mouse = crearProducto("Mouse", "50.00", 3);
        Long idItem = agregarAlCarrito(mouse, 5);

        mockMvc.perform(post("/api/pedidos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Stock insuficiente"))
                .andExpect(jsonPath("$.messages[0]").value(
                        "Stock insuficiente para \"Mouse\": se solicitan 5 y hay 3 disponibles"));

        // Nada cambió: stock intacto, ítem y carrito conservados
        assertEquals(3, productoRepository.findById(mouse.getId()).orElseThrow().getStock());
        assertTrue(itemCarritoRepository.existsById(idItem));
        assertTrue(carritoRepository.findByClienteId(1L).isPresent());
    }

    // ---------------------------------------------------------------
    // 4) Fallo de validación: clienteId inválido -> 400
    // ---------------------------------------------------------------
    @Test
    void pedidoConClienteIdInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/pedidos").param("clienteId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Pedido inválido"))
                .andExpect(jsonPath("$.messages[0]").value("El clienteId debe ser mayor a 0"));
    }
}
