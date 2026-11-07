package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ComparativaMetricaResponse;
import com.ejemplo.backenduserapi.dto.MetricaValoresResponse;
import com.ejemplo.backenduserapi.dto.MetricasVentasResponse;
import com.ejemplo.backenduserapi.dto.ProductoVendidoResponse;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.DetallePedidoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * HU 57 - Panel de métricas de ventas del vendedor autenticado.
 *
 * Reglas de negocio (verificadas contra el dominio real del proyecto):
 *  - Ingresos confirmados: solo los pedidos en estado "EN_PROCESO" o
 *    "ENTREGADO". Se excluyen "REGISTRADO" (pedido pendiente de procesar por
 *    el vendedor) y "CANCELADO". El proyecto NO tiene estados de pago, asi que
 *    no se inventa ninguno: la confirmacion es la del propio dominio.
 *  - Pedidos: se cuentan pedidos DISTINTOS que incluyen productos del
 *    vendedor, con la misma regla de estados que los ingresos (numerador y
 *    denominador son consistentes).
 *  - Importe atribuido: un pedido puede tener productos de varios vendedores;
 *    a cada uno se le atribuye SOLO la suma de sus lineas
 *    (cantidad x precioUnitario congelado en la compra). El total del pedido
 *    nunca se reparte porque el dominio no tiene descuentos, envios ni
 *    impuestos que distribuir.
 *  - Productos mas vendidos: unidades vendidas (SUM de cantidades), agrupadas
 *    por producto.
 *  - Fechas: criterio inclusivo por dia completo. `desde` empieza a las 00:00
 *    y `hasta` termina a las 23:59:59 del dia indicado. La zona horaria es la
 *    local del servidor, la misma en la que se registran `Pedido.fecha`
 *    (LocalDateTime.now()).
 *  - Periodo anterior: exactamente la misma duracion y sin solapamiento.
 */
@Service
public class MetricasVentasService {

    /** Estados del dominio que representan una venta confirmada. */
    public static final List<String> ESTADOS_CONFIRMADOS = List.of("EN_PROCESO", "ENTREGADO");

    /** Periodo por defecto cuando el vendedor no indica fechas: los ultimos 30 dias. */
    public static final int DIAS_POR_DEFECTO = 30;

    /** Cantidad maxima de productos destacados devueltos en el panel. */
    public static final int MAXIMO_PRODUCTOS_DESTACADOS = 10;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int ESCALA_MONEDA = 2;

    private final DetallePedidoRepository detalles;

    public MetricasVentasService(DetallePedidoRepository detalles) {
        this.detalles = detalles;
    }

    /**
     * Calcula las metricas del periodo solicitado y, cuando corresponde, las
     * del periodo anterior de igual duracion.
     */
    @Transactional(readOnly = true)
    public MetricasVentasResponse consultar(Usuario vendedor, LocalDate desde, LocalDate hasta,
                                            boolean compararPeriodoAnterior) {
        exigirVendedor(vendedor);

        if ((desde == null) != (hasta == null)) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Indica las dos fechas del periodo: desde y hasta.");
        }
        if (desde == null) {
            LocalDate hoy = LocalDate.now();
            hasta = hoy;
            desde = hoy.minusDays(DIAS_POR_DEFECTO - 1L);
        }
        if (desde.isAfter(hasta)) {
            throw error(HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la fecha final.");
        }

        long duracion = ChronoUnit.DAYS.between(desde, hasta) + 1;
        LocalDate desdeAnterior = desde.minusDays(duracion);
        LocalDate hastaAnterior = hasta.minusDays(duracion);

        MetricaValoresResponse actual = valores(vendedor.getId(), desde, hasta);
        List<ProductoVendidoResponse> destacados = productosDestacados(vendedor.getId(), desde, hasta);

        if (!compararPeriodoAnterior) {
            return new MetricasVentasResponse(desde.toString(), hasta.toString(), null, null,
                    false, actual, null, null, null, null, destacados);
        }

        MetricaValoresResponse anterior = valores(vendedor.getId(), desdeAnterior, hastaAnterior);
        return new MetricasVentasResponse(
                desde.toString(), hasta.toString(),
                desdeAnterior.toString(), hastaAnterior.toString(),
                true, actual, anterior,
                comparativa(actual.ingresosConfirmados(), anterior.ingresosConfirmados()),
                comparativa(BigDecimal.valueOf(actual.cantidadPedidos()),
                        BigDecimal.valueOf(anterior.cantidadPedidos())),
                comparativa(actual.promedioPorPedido(), anterior.promedioPorPedido()),
                destacados);
    }

    private MetricaValoresResponse valores(Long vendedorId, LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime finExclusivo = hasta.plusDays(1).atStartOfDay();

        BigDecimal ingresos = detalles.sumarIngresosConfirmados(vendedorId, ESTADOS_CONFIRMADOS, inicio, finExclusivo);
        if (ingresos == null) {
            ingresos = BigDecimal.ZERO;
        }
        Long pedidos = detalles.contarPedidosConfirmados(vendedorId, ESTADOS_CONFIRMADOS, inicio, finExclusivo);
        long cantidadPedidos = pedidos == null ? 0L : pedidos;

        // Promedio coherente: mismos pedidos que los ingresos y nunca division por cero.
        BigDecimal promedio = (cantidadPedidos == 0L)
                ? BigDecimal.ZERO.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP)
                : ingresos.divide(BigDecimal.valueOf(cantidadPedidos), ESCALA_MONEDA, RoundingMode.HALF_UP);

        return new MetricaValoresResponse(ingresos.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP),
                cantidadPedidos, promedio);
    }

    private List<ProductoVendidoResponse> productosDestacados(Long vendedorId, LocalDate desde, LocalDate hasta) {
        List<Object[]> filas = detalles.productosMasVendidos(vendedorId, ESTADOS_CONFIRMADOS,
                desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay(),
                PageRequest.of(0, MAXIMO_PRODUCTOS_DESTACADOS));

        List<ProductoVendidoResponse> productos = new ArrayList<>(filas.size());
        for (Object[] fila : filas) {
            Long unidades = numero(fila[3]);
            BigDecimal importe = numeroDecimal(fila[4]);
            productos.add(new ProductoVendidoResponse(
                    id(fila[0]),
                    (String) fila[1],
                    (String) fila[2],
                    unidades,
                    importe == null ? BigDecimal.ZERO.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP)
                            : importe.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP)));
        }
        return productos;
    }

    /**
     * Diferencia absoluta, variacion porcentual y tendencia.
     * La variacion queda en null cuando el periodo anterior fue cero: no se
     * inventa un porcentaje imposible de calcular.
     */
    private ComparativaMetricaResponse comparativa(BigDecimal actual, BigDecimal anterior) {
        BigDecimal diferencia = actual.subtract(anterior);
        BigDecimal variacion = (anterior.signum() == 0)
                ? null
                : diferencia.multiply(CIEN).divide(anterior, ESCALA_MONEDA, RoundingMode.HALF_UP);
        String tendencia = (diferencia.signum() > 0)
                ? ComparativaMetricaResponse.AUMENTO
                : (diferencia.signum() < 0)
                        ? ComparativaMetricaResponse.DISMINUCION
                        : ComparativaMetricaResponse.IGUAL;
        return new ComparativaMetricaResponse(
                diferencia.setScale(ESCALA_MONEDA, RoundingMode.HALF_UP), variacion, tendencia);
    }

    private void exigirVendedor(Usuario vendedor) {
        if (vendedor == null) {
            throw error(HttpStatus.UNAUTHORIZED, "Sesion requerida.");
        }
        if (vendedor.getRol() != Rol.VENDEDOR) {
            throw error(HttpStatus.FORBIDDEN, "Solo un vendedor puede consultar sus metricas de ventas.");
        }
    }

    private Long id(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }

    private Long numero(Object valor) {
        return valor == null ? 0L : ((Number) valor).longValue();
    }

    private BigDecimal numeroDecimal(Object valor) {
        return valor == null ? BigDecimal.ZERO : new BigDecimal(valor.toString());
    }

    private ExperienciaCompraException error(HttpStatus estado, String mensaje) {
        return new ExperienciaCompraException(estado, mensaje);
    }
}
