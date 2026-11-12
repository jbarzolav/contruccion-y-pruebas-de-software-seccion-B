package com.ejemplo.publicarproducto.model.conversaciones

data class ConsultaRequest(val productoId: Long, val mensaje: String)
data class RespuestaConsultaRequest(val mensaje: String)
data class ConfiguracionConsultas(val longitudMaxima: Int)
data class MensajeConsulta(val id: Long, val emisorId: Long, val emisorRol: String, val contenido: String, val fecha: String)
data class ConversacionResponse(val idConversacion: Long, val productoId: Long, val productoNombre: String,
    val productoImagenUrl: String?, val productoDisponible: Boolean? = null,
    val fechaCreacion: String, val mensajes: List<MensajeConsulta>)
data class ConversacionResumenResponse(val idConversacion: Long, val productoId: Long, val productoNombre: String,
    val productoImagenUrl: String?, val productoDisponible: Boolean? = null,
    val fechaCreacion: String, val ultimoMensaje: MensajeConsulta?)
data class PaginaMensajesResponse(val mensajes: List<MensajeConsulta>, val pagina: Int, val tamano: Int,
    val totalElementos: Long, val totalPaginas: Int, val tieneSiguiente: Boolean)
