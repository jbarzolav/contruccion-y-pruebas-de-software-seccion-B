package com.ejemplo.publicarproducto.model

// HU 23 - Datos administrativos sin contraseña ni hash.
data class UsuarioRolResponse(val id: Long, val correo: String, val rol: String, val permisos: Set<String>)
data class AsignarRolRequest(val rol: String)
