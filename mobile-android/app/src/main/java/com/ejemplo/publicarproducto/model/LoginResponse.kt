package com.ejemplo.publicarproducto.model

/**
 * HU 21 - respuesta del backend al iniciar sesión (HTTP 200):
 * { "token": "<uuid>", "id": 1, "correo": "..." }
 *
 * El token es opaco (UUID). No incluye password ni passwordHash.
 */
data class LoginResponse(
    val token: String,
    val id: Long,
    val correo: String
)
