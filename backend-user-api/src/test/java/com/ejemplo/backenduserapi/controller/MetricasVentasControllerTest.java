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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 57 - Panel de métricas de ventas del vendedor (integración con H2).
 *
 * Verifica sobre datos reales: agregaciones del backend, exclusión de pedidos
 * REGISTRADO (pendientes) y CANCELADO, atribución por vendedor en pedidos
 * compartidos, aislamiento entre vendedores, periodos anteriores, fechas
 * límite y permisos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetricasVentasControllerTest extends SesionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    private static final long VENDEDOR = 401L;
    private static final long OTRO_VENDEDOR = 402L;
    private static final long CLIENTE = 403L;
    private static final long VENDEDOR_SIN_VENTAS = 404L;

    private Producto mouse;
    private Producto teclado;
    private Producto cable;

    @BeforeEach
    void preparar() {
        limpiar();
        mouse = producto("Mouse inalambrico", new BigDecimal("100.00"), VENDEDOR);
        teclado = producto("Teclado mecanico", new BigDecimal("50.00"), VENDEDOR);
        cable = producto("Cable USB", new BigDecimal("30.00"), OTRO_VENDEDOR);

        // Pedido compartido entre dos vendedores (EN_PROCESO): 200 del vendedor 1 y 30 del 2.
        pedido(LocalDateTime.of(2026, 10, 5, 10, 0), "EN_PROCESO", new Object[][]{
                {mouse, 2, "100.00"}, {cable, 1, "30.00"}});
        // Pedido propio entregado.
        pedido(LocalDateTime.of(2026, 10, 6, 9, 0), "ENTREGADO", new Object[][]{
                {mouse, 1, "100.00"}});
        // Pendiente (REGISTRADO): no cuenta como venta confirmada.
        pedido(LocalDateTime.of(2026, 10, 7, 9, 0), "REGISTRADO", new Object[][]{
                {mouse, 5, "100.00"}});
        // Cancelado: no cuenta.
        pedido(LocalDateTime.of(2026, 10, 8, 9, 0), "CANCELADO", new Object[][]{
                {mouse, 5, "100.00"}});
        // Segundo pedido propio en el periodo.
        pedido(LocalDateTime.of(2026, 10, 9, 9, 0), "EN_PROCESO", new Object[][]{
                {teclado, 1, "50.00"}});
        // Fuera del periodo (cambio de mes).
        pedido(LocalDateTime.of(2026, 11, 2, 9, 0), "EN_PROCESO", new Object[][]{
                {mouse, 1, "100.00"}});
        // Periodo anterior (mismo numero de dias, un mes antes).
        pedido(LocalDateTime.of(2026, 9, 10, 9, 0), "ENTREGADO", new Object[][]{
                {mouse, 1, "100.00"}});
        // Cambio de anio.
        pedido(LocalDateTime.of(2025, 12, 25, 9, 0), "ENTREGADO", new Object[][]{
                {teclado, 1, "50.00"}});
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

    @Test
    void devuelveIngresosPedidosPromedioYProductosDelVendedor() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                // 200 (pedido compartido) + 100 (entregado) + 50 (segundo pedido).
                .andExpect(jsonPath("$.periodoActual.ingresosConfirmados").value(350.00))
                // El pedido compartido cuenta una sola vez.
                .andExpect(jsonPath("$.periodoActual.cantidadPedidos").value(3))
                .andExpect(jsonPath("$.periodoActual.promedioPorPedido").value(116.67))
                .andExpect(jsonPath("$.productosMasVendidos[0].idProducto").value(mouse.getId()))
                .andExpect(jsonPath("$.productosMasVendidos[0].unidadesVendidas").value(3))
                .andExpect(jsonPath("$.productosMasVendidos[0].importeAtribuido").value(300.00))
                .andExpect(jsonPath("$.productosMasVendidos[1].nombre").value("Teclado mecanico"));
    }

    @Test
    void excluyePedidosPendientesYCancelados() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31")
                        .param("compararPeriodoAnterior", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoActual.ingresosConfirmados").value(350.00))
                .andExpect(jsonPath("$.periodoActual.cantidadPedidos").value(3))
                .andExpect(jsonPath("$.periodoAnterior").doesNotExist())
                .andExpect(jsonPath("$.comparativaIngresos").doesNotExist());
    }

    @Test
    void atribuyeSoloLaParteDelVendedorEnPedidosCompartidos() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(OTRO_VENDEDOR))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoActual.ingresosConfirmados").value(30.00))
                .andExpect(jsonPath("$.periodoActual.cantidadPedidos").value(1))
                .andExpect(jsonPath("$.productosMasVendidos[0].nombre").value("Cable USB"));
    }

    @Test
    void noMuestraDatosDeOtrosVendedores() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                // El cable pertenece al otro vendedor: no aparece en su panel.
                .andExpect(jsonPath("$.productosMasVendidos.length()").value(2))
                .andExpect(jsonPath("$.productosMasVendidos[0].nombre").value("Mouse inalambrico"));
    }

    @Test
    void comparaConElPeriodoAnteriorDeIgualDuracion() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desdeAnterior").value("2026-08-31"))
                .andExpect(jsonPath("$.hastaAnterior").value("2026-09-30"))
                .andExpect(jsonPath("$.periodoAnterior.ingresosConfirmados").value(100.00))
                .andExpect(jsonPath("$.periodoAnterior.cantidadPedidos").value(1))
                .andExpect(jsonPath("$.comparativaIngresos.diferenciaAbsoluta").value(250.00))
                .andExpect(jsonPath("$.comparativaIngresos.variacionPorcentual").value(250.00))
                .andExpect(jsonPath("$.comparativaIngresos.tendencia").value("AUMENTO"));
    }

    @Test
    void unVendedorSinVentasRecibeCerosYNoFalla() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR_SIN_VENTAS))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoActual.ingresosConfirmados").value(0.00))
                .andExpect(jsonPath("$.periodoActual.cantidadPedidos").value(0))
                .andExpect(jsonPath("$.periodoActual.promedioPorPedido").value(0.00))
                .andExpect(jsonPath("$.productosMasVendidos.length()").value(0));
    }

    @Test
    void unPeriodoSinVentasDevuelveCerosYListaVacia() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR))
                        .param("desde", "2027-01-01")
                        .param("hasta", "2027-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoActual.ingresosConfirmados").value(0.00))
                .andExpect(jsonPath("$.periodoActual.cantidadPedidos").value(0))
                .andExpect(jsonPath("$.periodoActual.promedioPorPedido").value(0.00))
                .andExpect(jsonPath("$.productosMasVendidos.length()").value(0));
    }

    @Test
    void elCriteriaDeFechaEsInclusivoPorDia() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR))
                        .param("desde", "2026-10-05")
                        .param("hasta", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoActual.ingresosConfirmados").value(200.00))
                .andExpect(jsonPath("$.periodoActual.cantidadPedidos").value(1));
    }

    @Test
    void elPeriodoPuedeCruzarElCambioDeAnio() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR))
                        .param("desde", "2025-12-20")
                        .param("hasta", "2026-01-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoActual.ingresosConfirmados").value(50.00))
                .andExpect(jsonPath("$.periodoActual.cantidadPedidos").value(1))
                .andExpect(jsonPath("$.desdeAnterior").value("2025-11-28"))
                .andExpect(jsonPath("$.hastaAnterior").value("2025-12-19"));
    }

    @Test
    void rechazaLaFechaInicialPosteriorALaFinal() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoVendedor(VENDEDOR))
                        .param("desde", "2026-10-31")
                        .param("hasta", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("La fecha inicial no puede ser posterior a la fecha final."));
    }

    @Test
    void rechazaUnRolQueNoEsVendedor() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .with(comoCliente(CLIENTE))
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rechazaLaConsultaSinSesion() throws Exception {
        mockMvc.perform(get("/api/vendedores/me/metricas/ventas")
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isUnauthorized());
    }

    private Producto producto(String nombre, BigDecimal precio, long vendedorId) {
        Producto producto = new Producto(null, nombre, "Descripcion", precio, 10,
                "Accesorios", "DISPONIBLE", "/imagenes/" + nombre + ".png", vendedorId);
        return productoRepository.save(producto);
    }

    private Pedido pedido(LocalDateTime fecha, String estado, Object[][] lineas) {
        Pedido pedido = new Pedido(999L, fecha);
        pedido.setEstado(estado);
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] linea : lineas) {
            Producto producto = (Producto) linea[0];
            int cantidad = (Integer) linea[1];
            BigDecimal precio = new BigDecimal((String) linea[2]);
            pedido.getDetalles().add(new DetallePedido(pedido, producto, cantidad, precio));
            total = total.add(precio.multiply(BigDecimal.valueOf(cantidad)));
        }
        pedido.setTotal(total);
        return pedidoRepository.save(pedido);
    }
}
