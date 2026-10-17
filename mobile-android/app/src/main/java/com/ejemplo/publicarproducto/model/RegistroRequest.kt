package com.ejemplo.publicarproducto.model

/**
 * HU 26 - DTO que envía la app al backend: POST /api/auth/registro
 * El rol nunca se envía: el backend asigna siempre CLIENTE.
 */
data class RegistroRequest(
    val correo: String,
    val password: String,
    val confirmacionPassword: String
)
