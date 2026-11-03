package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.DetallePedidoRepository;
import com.ejemplo.backenduserapi.repository.HistorialEstadoPedidoRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 49 - PATCH /api/pedidos/{idPedido}/estado
 * transición válida · transición no permitida · vendedor ajeno ·
 * pedido inexistente · acceso sin autenticar.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu49estado;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PedidoEstadoActualizacionControllerTest extends SesionControllerTest {

    private static final Long VENDEDOR = 40L;
    private static final Long OTRO_VENDEDOR = 41L;
    private static final Long CLIENTE = 10L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private DetallePedidoRepository detallePedidoRepository;
    @Autowired private HistorialEstadoPedidoRepository historialRepository;

    @BeforeEach
    void preparar() {
        limpiar();
    }

    @AfterEach
    void finalizar() {
        limpiar();
    }

    /** Orden por claves foráneas: historial → detalles → pedidos → productos. */
    private void limpiar() {
        historialRepository.deleteAll();
        detallePedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    private Producto crearProducto(String nombre, String precio, Long vendedorId) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para pruebas HU49");
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(10);
        producto.setCategoria("Tecnología");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(vendedorId);
        return productoRepository.save(producto);
    }

    private Pedido crearPedido(Producto producto, Long vendedorId) {
        Pedido pedido = pedidoRepository.save(new Pedido(CLIENTE, LocalDateTime.now()));
        detallePedidoRepository.save(new DetallePedido(pedido, producto, 1, producto.getPrecio()));
        return pedido;
    }

    // ---------------------------------------------------------------
    // 1) Transición válida -> 200 con estado, fecha, historial y siguientes estados
    // ---------------------------------------------------------------
    @Test
    void vendedorActualizaElEstadoDeSuPedido() throws Exception {
        Producto producto = crearProducto("Laptop", "800.00", VENDEDOR);
        Pedido pedido = crearPedido(producto, VENDEDOR);

        mockMvc.perform(patch("/api/pedidos/{idPedido}/estado", pedido.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PROCESO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPedido").value(pedido.getId()))
                .andExpect(jsonPath("$.estadoAnterior").value("REGISTRADO"))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.fechaActualizacion").exists())
                .andExpect(jsonPath("$.estadosPermitidos[0]").value("ENTREGADO"))
                .andExpect(jsonPath("$.historial.length()").value(1))
                .andExpect(jsonPath("$.historial[0].estadoNuevo").value("EN_PROCESO"));

        assertEquals("EN_PROCESO", pedidoRepository.findById(pedido.getId()).orElseThrow().getEstado());
        assertEquals(1, historialRepository.findByPedidoIdOrderByFechaCambioDesc(pedido.getId()).size());
    }

    // ---------------------------------------------------------------
    // 2) Transición no permitida -> 400 y el estado no cambia
    // ---------------------------------------------------------------
    @Test
    void transicionNoPermitidaDevuelve400() throws Exception {
        Producto producto = crearProducto("Laptop", "800.00", VENDEDOR);
        Pedido pedido = crearPedido(producto, VENDEDOR);

        mockMvc.perform(patch("/api/pedidos/{idPedido}/estado", pedido.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages[0]").value("No se permite pasar de REGISTRADO a ENTREGADO."));

        assertEquals("REGISTRADO", pedidoRepository.findById(pedido.getId()).orElseThrow().getEstado());
        assertTrue(historialRepository.findByPedidoIdOrderByFechaCambioDesc(pedido.getId()).isEmpty());
    }

    // ---------------------------------------------------------------
    // 3) Vendedor sin productos en el pedido -> 403
    // ---------------------------------------------------------------
    @Test
    void vendedorAjenoNoPuedeActualizarElPedido() throws Exception {
        Producto producto = crearProducto("Laptop", "800.00", VENDEDOR);
        Pedido pedido = crearPedido(producto, VENDEDOR);

        mockMvc.perform(patch("/api/pedidos/{idPedido}/estado", pedido.getId())
                        .with(comoVendedor(OTRO_VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PROCESO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.messages[0]").value("El pedido no incluye productos de tus publicaciones."));
    }

    // ---------------------------------------------------------------
    // 4) Pedido inexistente -> 404
    // ---------------------------------------------------------------
    @Test
    void pedidoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(patch("/api/pedidos/{idPedido}/estado", 987654L)
                        .with(comoVendedor(VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PROCESO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.messages[0]").value("Pedido no encontrado."));
    }

    // ---------------------------------------------------------------
    // 5) Sin autenticar -> 401
    // ---------------------------------------------------------------
    @Test
    void accesoSinAutenticarDevuelve401() throws Exception {
        Producto producto = crearProducto("Laptop", "800.00", VENDEDOR);
        Pedido pedido = crearPedido(producto, VENDEDOR);

        mockMvc.perform(patch("/api/pedidos/{idPedido}/estado", pedido.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PROCESO\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---------------------------------------------------------------
    // 6) Un cliente no puede actualizar el estado (403)
    // ---------------------------------------------------------------
    @Test
    void clienteNoPuedeActualizarElEstado() throws Exception {
        Producto producto = crearProducto("Laptop", "800.00", VENDEDOR);
        Pedido pedido = crearPedido(producto, VENDEDOR);

        mockMvc.perform(patch("/api/pedidos/{idPedido}/estado", pedido.getId())
                        .with(comoCliente(CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PROCESO\"}"))
                .andExpect(status().isForbidden());
    }
}
