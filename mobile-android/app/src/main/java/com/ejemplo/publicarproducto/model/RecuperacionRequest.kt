package com.ejemplo.publicarproducto.model

// HU 22 - Solicitud del código, verificación, nueva contraseña y respuestas.
data class RecuperacionRequest(val correo: String)

// HU 22 - Paso intermedio del flujo: código de 6 dígitos recibido por correo.
data class CodigoRecuperacionRequest(val codigo: String)

data class RestablecerRequest(val token: String, val password: String, val confirmacionPassword: String)

// Respuesta de POST /api/auth/recuperacion y /api/auth/restablecer.
data class RecuperacionResponse(val mensaje: String)

// Respuesta de POST /api/auth/recuperacion/verificar:
// {valido: true, mensaje: "Código válido."} o error 400/429 con messages.
data class ValidacionCodigoResponse(val valido: Boolean, val mensaje: String)
