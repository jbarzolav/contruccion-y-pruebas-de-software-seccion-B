package com.ejemplo.publicarproducto.model

/**
 * HU 29 - DTO que envía la app al backend: PATCH /api/vendedores/me/perfil
 * No incluye usuarioId: el propietario lo determina la sesión Bearer.
 */
data class PerfilVendedorRequest(
    val nombreComercial: String,
    val descripcion: String,
    val telefonoContacto: String
)

/**
 * HU 29 - Perfil comercial del vendedor autenticado (sin secretos).
 */
data class PerfilVendedorResponse(
    val usuarioId: Long? = null,
    val nombreComercial: String? = null,
    val descripcion: String? = null,
    val telefonoContacto: String? = null
)
