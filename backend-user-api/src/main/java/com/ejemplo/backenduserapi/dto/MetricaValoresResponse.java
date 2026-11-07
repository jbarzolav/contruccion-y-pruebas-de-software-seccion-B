package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;

/**
 * HU 57 - Valores de una métrica de ventas en un periodo concreto.
 *
 * `ingresosConfirmados` suma SOLO el importe de las líneas de los productos del
 * vendedor (un pedido compartido aporta a cada vendedor únicamente su parte).
 * `cantidadPedidos` cuenta pedidos distintos que incluyen productos del
 * vendedor. `promedioPorPedido` es ingresos/cantidadPedidos y vale cero cuando
 * no hay pedidos elegibles (nunca se divide entre cero).
 */
public record MetricaValoresResponse(BigDecimal ingresosConfirmados,
                                     Long cantidadPedidos,
                                     BigDecimal promedioPorPedido) {
}
