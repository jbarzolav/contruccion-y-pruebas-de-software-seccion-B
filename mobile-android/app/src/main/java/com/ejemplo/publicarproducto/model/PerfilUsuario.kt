package com.ejemplo.publicarproducto.model

/**
 * HU 28 - DTO que envía la app al backend: PATCH /api/usuarios/me
 * Solo contiene campos personales habilitados: nunca envía id, rol,
 * permisos ni contraseña (la identidad la decide la sesión Bearer).
 */
data class ActualizarPerfilRequest(
    val nombre: String,
    val correo: String,
    val telefono: String?
)

/**
 * HU 27 / HU 28 - Perfil del usuario autenticado: extiende el DTO de HU 27
 * con los datos personales editables (nombre y teléfono).
 */
data class PerfilUsuarioResponse(
    val id: Long,
    val nombre: String? = null,
    val correo: String,
    val telefono: String? = null,
    val rol: String,
    val permisos: Set<String>
)
