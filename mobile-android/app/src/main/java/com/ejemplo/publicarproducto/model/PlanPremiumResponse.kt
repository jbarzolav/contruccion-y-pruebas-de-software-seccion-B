package com.ejemplo.publicarproducto.model

// HU 18 - Información del plan Premium recibida desde Spring Boot.
data class PlanPremiumResponse(
    val nombre: String,
    val descripcion: String,
    val beneficios: List<String>
)
