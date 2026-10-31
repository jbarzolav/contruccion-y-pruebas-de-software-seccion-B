package com.ejemplo.publicarproducto.model

import java.math.BigDecimal

/** HU 43 - Resumen público de calificaciones de un vendedor. */
data class CalificacionesVendedorResumenResponse(
    val vendedorId: Long? = null,
    val promedio: BigDecimal = BigDecimal.ZERO,
    val cantidad: Long = 0,
    val distribucion: Map<String, Long> = emptyMap()
)
