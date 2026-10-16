package com.ejemplo.publicarproducto.model

// HU 22 - Solicitud del enlace, nueva contraseña y respuesta del backend.
data class RecuperacionRequest(val correo: String)
data class RestablecerRequest(val token: String, val password: String, val confirmacionPassword: String)
data class RecuperacionResponse(val mensaje: String)
