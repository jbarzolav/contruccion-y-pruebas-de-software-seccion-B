package com.ejemplo.publicarproducto.network

import retrofit2.Response
import retrofit2.http.*

data class ConsultaRequest(val productoId: Long, val mensaje: String)
data class RespuestaConsultaRequest(val mensaje: String)
data class ConfiguracionConsultas(val longitudMaxima: Int)
data class MensajeConsulta(val id: Long, val emisorId: Long, val emisorRol: String, val contenido: String, val fecha: String)
data class ConversacionResponse(val idConversacion: Long, val productoId: Long, val productoNombre: String,
    val fechaCreacion: String, val mensajes: List<MensajeConsulta>)

interface ConversacionApi {
    @GET("api/conversaciones/configuracion") suspend fun configuracion(): Response<ConfiguracionConsultas>
    @GET("api/conversaciones") suspend fun listar(): Response<List<ConversacionResponse>>
    @POST("api/conversaciones") suspend fun consultar(@Body cuerpo: ConsultaRequest): Response<ConversacionResponse>
    @POST("api/conversaciones/{id}/mensajes") suspend fun responder(@Path("id") id: Long,
        @Body cuerpo: RespuestaConsultaRequest): Response<MensajeConsulta>
}
