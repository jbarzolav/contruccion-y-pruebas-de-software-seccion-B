package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interfaz Retrofit del endpoint de la HU 01.
 */
interface ProductoApi {

    @POST("api/productos")
    suspend fun crearProducto(@Body request: ProductoRequest): Response<ProductoResponse>
}
