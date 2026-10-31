package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;
import java.util.Map;

/** HU 43 - Resumen público de las calificaciones de un vendedor. */
public record CalificacionesVendedorResumenResponse(
        Long vendedorId,
        BigDecimal promedio,
        long cantidad,
        Map<Integer, Long> distribucion
) {
}
