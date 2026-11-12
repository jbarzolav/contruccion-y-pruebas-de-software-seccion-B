package com.ejemplo.publicarproducto.model.remote

import retrofit2.Response
import retrofit2.http.*

typealias ConsultaRequest = com.ejemplo.publicarproducto.model.conversaciones.ConsultaRequest
typealias RespuestaConsultaRequest = com.ejemplo.publicarproducto.model.conversaciones.RespuestaConsultaRequest
typealias ConfiguracionConsultas = com.ejemplo.publicarproducto.model.conversaciones.ConfiguracionConsultas
typealias MensajeConsulta = com.ejemplo.publicarproducto.model.conversaciones.MensajeConsulta
typealias ConversacionResponse = com.ejemplo.publicarproducto.model.conversaciones.ConversacionResponse
typealias ConversacionResumenResponse = com.ejemplo.publicarproducto.model.conversaciones.ConversacionResumenResponse
typealias PaginaMensajesResponse = com.ejemplo.publicarproducto.model.conversaciones.PaginaMensajesResponse

interface ConversacionApi {
    @GET("api/conversaciones/configuracion") suspend fun configuracion(): Response<ConfiguracionConsultas>
    @GET("api/conversaciones") suspend fun listar(): Response<List<ConversacionResumenResponse>>
    @POST("api/conversaciones") suspend fun consultar(@Body cuerpo: ConsultaRequest): Response<ConversacionResponse>
    @GET("api/conversaciones/{id}/mensajes") suspend fun mensajes(@Path("id") id: Long,
        @Query("pagina") pagina: Int = 0, @Query("tamano") tamano: Int = 20): Response<PaginaMensajesResponse>
    @POST("api/conversaciones/{id}/mensajes") suspend fun responder(@Path("id") id: Long,
        @Body cuerpo: RespuestaConsultaRequest): Response<MensajeConsulta>
}
