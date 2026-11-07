package com.ejemplo.backenduserapi.dto;

import java.util.List;

/**
 * HU 57 - Panel de métricas de ventas del vendedor autenticado.
 *
 * El periodo actual es el solicitado (desde/hasta, ambos inclusive por día).
 * El periodo anterior tiene exactamente la misma duración y termina el día
 * anterior al inicio del periodo actual (sin solapamiento).
 * Cuando `compararPeriodoAnterior` es false, `periodoAnterior` y las tres
 * comparativas llegan en null: la interfaz no inventa datos.
 */
public record MetricasVentasResponse(String desde,
                                      String hasta,
                                      String desdeAnterior,
                                      String hastaAnterior,
                                      Boolean compararPeriodoAnterior,
                                      MetricaValoresResponse periodoActual,
                                      MetricaValoresResponse periodoAnterior,
                                      ComparativaMetricaResponse comparativaIngresos,
                                      ComparativaMetricaResponse comparativaPedidos,
                                      ComparativaMetricaResponse comparativaPromedio,
                                      List<ProductoVendidoResponse> productosMasVendidos) {
}
