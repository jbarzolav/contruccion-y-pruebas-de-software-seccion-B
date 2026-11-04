package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.DetallePedidoRepository;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 50 - GET /api/pedidos/{idPedido}
 * detalle exacto (líneas y total persistidos) · pedido inexistente ·
 * pedido de otro cliente · acceso sin autenticar.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu50detalle;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PedidoDetalleControllerTest extends SesionControllerTest {

    private static final Long CLIENTE = 10L;
    private static final Long OTRO_CLIENTE = 11L;
    private static final Long VENDEDOR = 40L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private DetallePedidoRepository detallePedidoRepository;

    @BeforeEach
    void preparar() {
        limpiar();
    }

    @AfterEach
    void finalizar() {
        limpiar();
    }

    private void limpiar() {
        detallePedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    private Producto crearProducto(String nombre, String precio) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para pruebas HU50");
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(10);
        producto.setCategoria("Oficina");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(VENDEDOR);
        return productoRepository.save(producto);
    }

    /**
     * El precioUnitario queda congelado en 50.00 aunque el catálogo cambie a
     * 999.00: el detalle debe mostrar el precio registrado en la operación.
     */
    private Pedido crearPedido() {
        Producto lapicero = crearProducto("Lapicero", "50.00");
        Producto cuaderno = crearProducto("Cuaderno", "30.00");

        Pedido pedido = pedidoRepository.save(new Pedido(CLIENTE, LocalDateTime.now()));
        pedido.setTotal(new BigDecimal("130.00"));
        pedidoRepository.save(pedido);

        detallePedidoRepository.save(new DetallePedido(pedido, lapicero, 2, new BigDecimal("50.00")));
        detallePedidoRepository.save(new DetallePedido(pedido, cuaderno, 1, new BigDecimal("30.00")));

        // El catálogo cambia después de la compra: no debe alterar el detalle.
        lapicero.setPrecio(new BigDecimal("999.00"));
        productoRepository.save(lapicero);

        return pedido;
    }

    // ---------------------------------------------------------------
    // 1) Detalle exacto: líneas, cantidades, precios y total persistido
    // ---------------------------------------------------------------
    @Test
    void devuelveElDetalleConLasLineasRegistradasEnLaOperacion() throws Exception {
        Pedido pedido = crearPedido();

        mockMvc.perform(get("/api/pedidos/{idPedido}", pedido.getId()).with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPedido").value(pedido.getId()))
                .andExpect(jsonPath("$.clienteId").value(CLIENTE))
                .andExpect(jsonPath("$.fecha").exists())
                .andExpect(jsonPath("$.estado").value("REGISTRADO"))
                .andExpect(jsonPath("$.total").value(130.00))
                .andExpect(jsonPath("$.detalles.length()").value(2))
                .andExpect(jsonPath("$.detalles[0].nombreProducto").value("Lapicero"))
                .andExpect(jsonPath("$.detalles[0].cantidad").value(2))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(50.00))
                .andExpect(jsonPath("$.detalles[0].subtotal").value(100.00))
                .andExpect(jsonPath("$.detalles[1].nombreProducto").value("Cuaderno"))
                .andExpect(jsonPath("$.detalles[1].subtotal").value(30.00));
    }

    // ---------------------------------------------------------------
    // 2) Pedido inexistente -> 404
    // ---------------------------------------------------------------
    @Test
    void pedidoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/pedidos/{idPedido}", 987654L).with(comoCliente(CLIENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.messages[0]").value("Pedido no encontrado."));
    }

    // ---------------------------------------------------------------
    // 3) Pedido de otro cliente -> 403
    // ---------------------------------------------------------------
    @Test
    void pedidoDeOtroClienteDevuelve403() throws Exception {
        Pedido pedido = crearPedido();

        mockMvc.perform(get("/api/pedidos/{idPedido}", pedido.getId()).with(comoCliente(OTRO_CLIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.messages[0]").value("No puedes operar con una compra de otro cliente."));
    }

    // ---------------------------------------------------------------
    // 4) Sin autenticar -> 401
    // ---------------------------------------------------------------
    @Test
    void accesoSinAutenticarDevuelve401() throws Exception {
        Pedido pedido = crearPedido();

        mockMvc.perform(get("/api/pedidos/{idPedido}", pedido.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---------------------------------------------------------------
    // 5) Identificador inválido -> 400; identificador inexistente -> 404
    // ---------------------------------------------------------------
    @Test
    void identificadorInvalidoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/pedidos/{idPedido}", "abc").with(comoCliente(CLIENTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void identificadorNoExistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/pedidos/{idPedido}", 0).with(comoCliente(CLIENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.messages[0]").value("Pedido no encontrado."));
    }

    // ---------------------------------------------------------------
    // 6) El total del detalle es el persistido, no una suma recalculada
    // ---------------------------------------------------------------
    @Test
    void elTotalDevueltoEsElPersistidoEnElPedido() throws Exception {
        Pedido pedido = crearPedido();
        pedido.setTotal(new BigDecimal("130.00"));
        pedidoRepository.save(pedido);

        var respuesta = mockMvc.perform(get("/api/pedidos/{idPedido}", pedido.getId()).with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(130.00, new BigDecimal(respuesta.replaceAll(".*\"total\":([^,}]+).*", "$1")).doubleValue(), 0.001);
    }
}
