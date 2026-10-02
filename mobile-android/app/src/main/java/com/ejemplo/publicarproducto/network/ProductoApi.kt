package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.ImagenProductoRequest
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ProductoApi {

    @POST("api/productos")
    suspend fun crearProducto(
        @Body request: ProductoRequest
    ): Response<ProductoResponse>

    @POST("api/productos/{idProducto}/imagenes")
    suspend fun agregarImagen(
        @Path("idProducto") idProducto: Long,
        @Body request: ImagenProductoRequest
    ): Response<ProductoResponse>
}