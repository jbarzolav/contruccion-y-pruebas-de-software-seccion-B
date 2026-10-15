package com.ejemplo.publicarproducto.model

/**
 * HU 21 - DTO que envía la app al backend: POST /api/auth/login
 * Solo correo y contraseña; nunca se registra ni se guarda.
 */
data class LoginRequest(
    val correo: String,
    val password: String
)
