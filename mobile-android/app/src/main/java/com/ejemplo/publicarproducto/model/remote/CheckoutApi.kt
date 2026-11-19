package com.ejemplo.publicarproducto.model.remote

import retrofit2.http.*
import java.math.BigDecimal

data class SeleccionGrupo(val vendedorId: Long, val itemIds: List<Long>)
data class AvisoPago(val referencia: String, val importe: BigDecimal, val moneda: String, val resultado: String)
data class PagoGrupo(val idPedido: Long, val codigoPedido: String, val vendedorId: Long, val total: BigDecimal,
    val moneda: String, val estadoPago: String, val estadoEntrega: String, val referencia: String, val estadoReembolso: String?) {
    fun aviso(resultado: String) = AvisoPago(referencia, total, moneda, resultado)
}
interface CheckoutApi {
    @POST("api/pedidos/checkout/{id}/simulador/reembolso/solicitar") suspend fun solicitar(@Path("id") id:Long,@Body aviso:AvisoPago):PagoGrupo
    @POST("api/pedidos/checkout/{id}/simulador/reembolso/resultado") suspend fun devolver(@Path("id") id:Long,@Body aviso:AvisoPago):PagoGrupo
    @GET("api/conversaciones/entrega/{id}/mensajes") suspend fun mensajes(@Path("id") id: Long): List<MensajeEntregaDto>
    @POST("api/conversaciones/entrega/{id}/mensajes") suspend fun enviar(@Path("id") id: Long,@Body mensaje: TextoEntrega): MensajeEntregaDto
    @POST("api/pedidos/checkout") suspend fun crear(@Body seleccion: SeleccionGrupo): PagoGrupo
    @POST("api/pedidos/checkout/{id}/reintentar") suspend fun reintentar(@Path("id") id: Long): PagoGrupo
    @GET("api/conversaciones/entrega/{id}/pedido") suspend fun resumen(@Path("id") id: Long): PagoGrupo
    @POST("api/pedidos/checkout/{id}/simulador") suspend fun resolver(@Path("id") id: Long, @Body aviso: AvisoPago): PagoGrupo
}
data class TextoEntrega(val texto:String)
data class MensajeEntregaDto(val id:Long, val autorId:Long, val texto:String, val fecha:String)
