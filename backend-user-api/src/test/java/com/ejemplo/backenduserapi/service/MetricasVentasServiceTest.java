package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.DetallePedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * HU 57 - Cálculo de las métricas de ventas del vendedor.
 *
 * Prueba unitaria pura (sin Spring): se verifica la aritmética, el periodo
 * anterior de igual duración y las validaciones de fechas y permisos.
 */
@ExtendWith(MockitoExtension.class)
class MetricasVentasServiceTest {

    @Mock
    private DetallePedidoRepository detalles;

    @InjectMocks
    private MetricasVentasService servicio;

    private static final long VENDEDOR = 40L;

    @Test
    void calculaIngresosPedidosPromedioYProductosDelPeriodo() {
        Usuario vendedor = vendedor(VENDEDOR);
        when(detalles.sumarIngresosConfirmados(eq(VENDEDOR), anyList(), any(), any()))
                .thenReturn(new BigDecimal("1250.00"));
        when(detalles.contarPedidosConfirmados(eq(VENDEDOR), anyList(), any(), any())).thenReturn(10L);
        when(detalles.productosMasVendidos(eq(VENDEDOR), anyList(), any(), any(), any()))
                .thenReturn(Collections.singletonList(
                        new Object[]{7L, "Mouse", "/imagenes/mouse.png", 8L, new BigDecimal("600.00")}));

        var respuesta = servicio.consultar(vendedor,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false);

        assertEquals("2026-10-01", respuesta.desde());
        assertEquals("2026-10-31", respuesta.hasta());
        assertEquals(new BigDecimal("1250.00"), respuesta.periodoActual().ingresosConfirmados());
        assertEquals(10L, respuesta.periodoActual().cantidadPedidos());
        assertEquals(new BigDecimal("125.00"), respuesta.periodoActual().promedioPorPedido());
        assertEquals(1, respuesta.productosMasVendidos().size());
        assertEquals(7L, respuesta.productosMasVendidos().get(0).idProducto());
        assertEquals(8L, respuesta.productosMasVendidos().get(0).unidadesVendidas());
        assertEquals(new BigDecimal("600.00"), respuesta.productosMasVendidos().get(0).importeAtribuido());
        // Sin comparación no se inventan datos del periodo anterior.
        assertNull(respuesta.periodoAnterior());
        assertNull(respuesta.comparativaIngresos());
    }

    @Test
    void elPromedioEsCeroCuandoNoHayPedidos() {
        Usuario vendedor = vendedor(VENDEDOR);
        when(detalles.sumarIngresosConfirmados(eq(VENDEDOR), anyList(), any(), any())).thenReturn(null);
        when(detalles.contarPedidosConfirmados(eq(VENDEDOR), anyList(), any(), any())).thenReturn(0L);
        when(detalles.productosMasVendidos(eq(VENDEDOR), anyList(), any(), any(), any()))
                .thenReturn(List.of());

        var respuesta = servicio.consultar(vendedor,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false);

        assertEquals(new BigDecimal("0.00"), respuesta.periodoActual().ingresosConfirmados());
        assertEquals(0L, respuesta.periodoActual().cantidadPedidos());
        assertEquals(new BigDecimal("0.00"), respuesta.periodoActual().promedioPorPedido());
        assertEquals(List.of(), respuesta.productosMasVendidos());
    }

    @Test
    void elPeriodoAnteriorTieneLaMismaDuracionYNoSeSolapa() {
        Usuario vendedor = vendedor(VENDEDOR);
        when(detalles.sumarIngresosConfirmados(anyLong(), anyList(), any(), any()))
                .thenReturn(new BigDecimal("100.00"));
        when(detalles.contarPedidosConfirmados(anyLong(), anyList(), any(), any())).thenReturn(1L);
        when(detalles.productosMasVendidos(anyLong(), anyList(), any(), any(), any()))
                .thenReturn(List.of());

        // Del 1 al 15 de marzo (15 dias) -> el anterior es del 14 al 28 de febrero.
        var respuesta = servicio.consultar(vendedor,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15), true);

        assertEquals("2026-03-01", respuesta.desde());
        assertEquals("2026-03-15", respuesta.hasta());
        assertEquals("2026-02-14", respuesta.desdeAnterior());
        assertEquals("2026-02-28", respuesta.hastaAnterior());
    }

    @Test
    void calculaVariacionPositivaNegativaYEnCero() {
        Usuario vendedor = vendedor(VENDEDOR);
        when(detalles.sumarIngresosConfirmados(anyLong(), anyList(), any(), any()))
                .thenReturn(new BigDecimal("200.00"), new BigDecimal("100.00"));
        when(detalles.contarPedidosConfirmados(anyLong(), anyList(), any(), any()))
                .thenReturn(2L, 2L);
        when(detalles.productosMasVendidos(anyLong(), anyList(), any(), any(), any()))
                .thenReturn(List.of());

        var respuesta = servicio.consultar(vendedor,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), true);

        assertEquals(new BigDecimal("100.00"), respuesta.comparativaIngresos().diferenciaAbsoluta());
        assertEquals(new BigDecimal("100.00"), respuesta.comparativaIngresos().variacionPorcentual());
        assertEquals("AUMENTO", respuesta.comparativaIngresos().tendencia());
        assertEquals("IGUAL", respuesta.comparativaPedidos().tendencia());
        assertEquals(new BigDecimal("0.00"), respuesta.comparativaPedidos().diferenciaAbsoluta());
    }

    @Test
    void laVariacionQuedaVaciaCuandoElPeriodoAnteriorFueCero() {
        Usuario vendedor = vendedor(VENDEDOR);
        when(detalles.sumarIngresosConfirmados(anyLong(), anyList(), any(), any()))
                .thenReturn(new BigDecimal("500.00"), new BigDecimal("0.00"));
        when(detalles.contarPedidosConfirmados(anyLong(), anyList(), any(), any()))
                .thenReturn(1L, 0L);
        when(detalles.productosMasVendidos(anyLong(), anyList(), any(), any(), any()))
                .thenReturn(List.of());

        var respuesta = servicio.consultar(vendedor,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), true);

        assertNull(respuesta.comparativaIngresos().variacionPorcentual());
        assertEquals("AUMENTO", respuesta.comparativaIngresos().tendencia());
    }

    @Test
    void rechazaLaFechaInicialPosteriorALaFinal() {
        Usuario vendedor = vendedor(VENDEDOR);

        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.consultar(vendedor, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1), true));

        assertEquals(400, error.getEstado().value());
    }

    @Test
    void rechazaUnPeriodoConSoloUnaFecha() {
        Usuario vendedor = vendedor(VENDEDOR);

        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.consultar(vendedor, LocalDate.of(2026, 5, 1), null, true));

        assertEquals(400, error.getEstado().value());
    }

    @Test
    void rechazaACuentaQueNoEsVendedor() {
        Usuario cliente = new Usuario("cliente@test.com", "hash");
        cliente.setId(10L);
        cliente.setRol(Rol.CLIENTE);

        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.consultar(cliente, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), true));

        assertEquals(403, error.getEstado().value());
    }

    @Test
    void rechazaLaConsultaSinSesion() {
        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.consultar(null, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), true));

        assertEquals(401, error.getEstado().value());
    }

    private Usuario vendedor(long id) {
        Usuario usuario = new Usuario("vendedor@test.com", "hash");
        usuario.setId(id);
        usuario.setRol(Rol.VENDEDOR);
        return usuario;
    }
}
