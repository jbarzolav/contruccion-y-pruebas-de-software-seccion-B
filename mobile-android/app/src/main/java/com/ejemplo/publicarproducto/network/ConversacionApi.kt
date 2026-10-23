package com.ejemplo.publicarproducto.network

import retrofit2.Response
import retrofit2.http.*

data class ConsultaRequest(val productoId: Long, val mensaje: String)
data class RespuestaConsultaRequest(val mensaje: String)
data class ConfiguracionConsultas(val longitudMaxima: Int)
data class MensajeConsulta(val id: Long, val emisorId: Long, val emisorRol: String, val contenido: String, val fecha: String)
data class ConversacionResponse(val idConversacion: Long, val productoId: Long, val productoNombre: String,
    val fechaCreacion: String, val mensajes: List<MensajeConsulta>)
data class ConversacionResumenResponse(val idConversacion: Long, val productoId: Long, val productoNombre: String,
    val fechaCreacion: String, val ultimoMensaje: MensajeConsulta?)
data class PaginaMensajesResponse(val mensajes: List<MensajeConsulta>, val pagina: Int, val tamano: Int,
    val totalElementos: Long, val totalPaginas: Int, val tieneSiguiente: Boolean)

interface ConversacionApi {
    @GET("api/conversaciones/configuracion") suspend fun configuracion(): Response<ConfiguracionConsultas>
    @GET("api/conversaciones") suspend fun listar(): Response<List<ConversacionResumenResponse>>
    @POST("api/conversaciones") suspend fun consultar(@Body cuerpo: ConsultaRequest): Response<ConversacionResponse>
    @GET("api/conversaciones/{id}/mensajes") suspend fun mensajes(@Path("id") id: Long,
        @Query("pagina") pagina: Int = 0, @Query("tamano") tamano: Int = 20): Response<PaginaMensajesResponse>
    @POST("api/conversaciones/{id}/mensajes") suspend fun responder(@Path("id") id: Long,
        @Body cuerpo: RespuestaConsultaRequest): Response<MensajeConsulta>
}
