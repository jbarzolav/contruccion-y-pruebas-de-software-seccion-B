package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;

/**
 * HU 57 - Comparación de una métrica entre el periodo actual y el anterior.
 *
 * `diferenciaAbsoluta` es actual - anterior.
 * `variacionPorcentual` es la variación en porcentaje sobre el periodo anterior;
 * queda en null cuando el periodo anterior fue cero (no se inventa un valor).
 * `tendencia` es "AUMENTO", "DISMINUCION" o "IGUAL".
 */
public record ComparativaMetricaResponse(BigDecimal diferenciaAbsoluta,
                                         BigDecimal variacionPorcentual,
                                         String tendencia) {

    public static final String AUMENTO = "AUMENTO";
    public static final String DISMINUCION = "DISMINUCION";
    public static final String IGUAL = "IGUAL";
}
