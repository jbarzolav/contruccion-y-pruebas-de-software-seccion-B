package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.CarritoRepository;
import com.ejemplo.backenduserapi.repository.DetallePedidoRepository;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.ejemplo.backenduserapi.repository.UmbralStockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 58 - Umbrales de stock y avisos de stock bajo (integracion con H2).
 *
 * Recorre el ciclo real: configurar el umbral, vender (cruce por venta),
 * editar el stock a mano (cruce por edicion), comprobar que el aviso no se
 * repite, que la reposicion rearma la alerta y que el aviso aparece en el
 * sistema de notificaciones existente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UmbralStockControllerTest extends SesionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private ItemCarritoRepository itemCarritoRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private UmbralStockRepository umbralStockRepository;

    private static final long VENDEDOR = 501L;
    private static final long OTRO_VENDEDOR = 502L;
    private static final long CLIENTE = 503L;

    private Producto producto;

    @BeforeEach
    void preparar() {
        limpiar();
        producto = productoRepository.save(new Producto(null, "Mouse inalambrico", "Descripcion",
                new BigDecimal("100.00"), 10, "Accesorios", "DISPONIBLE",
                "/imagenes/mouse.png", VENDEDOR));
    }

    @AfterEach
    void finalizar() {
        limpiar();
    }

    private void limpiar() {
        itemCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
        detallePedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        notificacionRepository.deleteAll();
        umbralStockRepository.deleteAll();
        productoRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Configuracion y consulta del umbral
    // ------------------------------------------------------------------

    @Test
    void creaYActualizaElUmbralDeUnaPublicacionPropia() throws Exception {
        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"umbral\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idProducto").value(producto.getId()))
                .andExpect(jsonPath("$.stock").value(10))
                .andExpect(jsonPath("$.umbral").value(5))
                .andExpect(jsonPath("$.stockBajo").value(false));

        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"umbral\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbral").value(3));

        mockMvc.perform(get("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbral").value(3));
    }

    @Test
    void aceptaElUmbralCero() throws Exception {
        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"umbral\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbral").value(0));
    }

    @Test
    void rechazaElUmbralNegativo() throws Exception {
        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"umbral\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("El umbral no puede ser negativo"));
    }

    @Test
    void rechazaElUmbralAusente() throws Exception {
        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("El umbral es obligatorio"));
    }

    @Test
    void rechazaModificarElUmbralDeUnaPublicacionAjena() throws Exception {
        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(OTRO_VENDEDOR))
                        .contentType("application/json")
                        .content("{\"umbral\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rechazaUnaPublicacionInexistente() throws Exception {
        mockMvc.perform(get("/api/productos/{id}/umbral-stock", 999999L)
                        .with(comoVendedor(VENDEDOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazaLaConsultaSinSesion() throws Exception {
        mockMvc.perform(get("/api/productos/{id}/umbral-stock", producto.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaACuentaQueNoEsVendedor() throws Exception {
        mockMvc.perform(get("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoCliente(CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elPanelMuestraLasPublicacionesPropiasConSuUmbral() throws Exception {
        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"umbral\":5}"));

        mockMvc.perform(get("/api/vendedores/me/umbrales-stock")
                        .with(comoVendedor(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Mouse inalambrico"))
                .andExpect(jsonPath("$[0].stock").value(10))
                .andExpect(jsonPath("$[0].umbral").value(5))
                .andExpect(jsonPath("$[0].stockBajo").value(false));
    }

    // ------------------------------------------------------------------
    // Deteccion del cruce
    // ------------------------------------------------------------------

    @Test
    void laVentaQueCruzaElUmbralGeneraUnAviso() throws Exception {
        configurarUmbral(5);

        agregarAlCarrito(5);
        mockMvc.perform(post("/api/pedidos").with(comoCliente(CLIENTE)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/vendedores/me/avisos-stock-bajo").with(comoVendedor(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idProducto").value(producto.getId()))
                .andExpect(jsonPath("$[0].nombre").value("Mouse inalambrico"))
                .andExpect(jsonPath("$[0].stockActual").value(5))
                .andExpect(jsonPath("$[0].umbral").value(5))
                .andExpect(jsonPath("$[0].stockBajo").value(true));
    }

    @Test
    void laEdicionManualQueCruzaElUmbralGeneraUnAviso() throws Exception {
        configurarUmbral(5);

        editarStock(5);

        mockMvc.perform(get("/api/vendedores/me/avisos-stock-bajo").with(comoVendedor(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stockActual").value(5));
    }

    @Test
    void noRepiteElAvisoCuandoElStockSigueBajando() throws Exception {
        configurarUmbral(5);

        editarStock(5);
        editarStock(4);
        editarStock(3);

        mockMvc.perform(get("/api/vendedores/me/avisos-stock-bajo").with(comoVendedor(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void laReposicionRearmaLaAlertaYElSiguienteCruceVuelveAAvisar() throws Exception {
        configurarUmbral(5);

        editarStock(5);
        editarStock(9);
        editarStock(5);

        mockMvc.perform(get("/api/vendedores/me/avisos-stock-bajo").with(comoVendedor(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void configurarElUmbralConElStockYaBajoNoGeneraAviso() throws Exception {
        // Sin umbral todavia: bajar el stock no dispara ningun aviso.
        editarStock(3);

        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"umbral\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockBajo").value(true));

        mockMvc.perform(get("/api/vendedores/me/avisos-stock-bajo").with(comoVendedor(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elAvisoSeIntegraEnLaBandejaDeNotificaciones() throws Exception {
        configurarUmbral(5);
        editarStock(5);

        mockMvc.perform(get("/api/notificaciones/bandeja")
                        .with(comoVendedor(VENDEDOR))
                        .param("tipo", "STOCK_BAJO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones.length()").value(1))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value("STOCK_BAJO"))
                .andExpect(jsonPath("$.notificaciones[0].productoId").value(producto.getId()))
                .andExpect(jsonPath("$.notificaciones[0].enlace")
                        .value("/mis-publicaciones#producto-" + producto.getId()))
                .andExpect(jsonPath("$.notificaciones[0].estado").value("PENDIENTE"));
    }

    @Test
    void elVendedorNoVeLosAvisosDeOtroVendedor() throws Exception {
        configurarUmbral(5);
        editarStock(5);

        mockMvc.perform(get("/api/vendedores/me/avisos-stock-bajo").with(comoVendedor(OTRO_VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------
    // Ayudantes
    // ------------------------------------------------------------------

    private void configurarUmbral(int umbral) throws Exception {
        mockMvc.perform(put("/api/productos/{id}/umbral-stock", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"umbral\":" + umbral + "}"))
                .andExpect(status().isOk());
    }

    private void editarStock(int stock) throws Exception {
        mockMvc.perform(put("/api/productos/{id}", producto.getId())
                        .with(comoVendedor(VENDEDOR))
                        .param("vendedorId", String.valueOf(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"nombre\":\"Mouse inalambrico\",\"descripcion\":\"Descripcion\","
                                + "\"precio\":100.00,\"stock\":" + stock + ","
                                + "\"categoria\":\"Accesorios\",\"estado\":\"DISPONIBLE\","
                                + "\"imagenUrl\":\"/imagenes/mouse.png\","
                                + "\"vendedorId\":" + VENDEDOR + "}"))
                .andExpect(status().isOk());
    }

    private void agregarAlCarrito(int cantidad) throws Exception {
        mockMvc.perform(post("/api/carrito/items").with(comoCliente(CLIENTE))
                        .contentType("application/json")
                        .content("{\"productoId\":" + producto.getId() + ",\"cantidad\":" + cantidad + "}"))
                .andExpect(status().isCreated());
    }
}
