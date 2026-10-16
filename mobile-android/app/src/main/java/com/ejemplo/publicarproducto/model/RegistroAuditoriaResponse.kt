package com.ejemplo.publicarproducto.model

data class RegistroAuditoriaResponse(
    val id: Long,
    val accion: String,
    val usuario: String,
    val fecha: String
)

data class PaginaAuditoriaResponse(
    val contenido: List<RegistroAuditoriaResponse> = emptyList(),
    val pagina: Int = 0,
    val tamano: Int = 20,
    val totalElementos: Long = 0,
    val totalPaginas: Int = 0
)

data class AuthMessageResponse(val mensaje: String)
