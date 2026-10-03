package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ProductoApi {

    @POST("api/productos")
    suspend fun crearProducto(
        @Body request: ProductoRequest
    ): Response<ProductoResponse>

    /**
     * HU 02 - Asocia la imagen real del producto.
     * POST /api/productos/{idProducto}/imagenes (multipart/form-data)
     *
     * @param file archivo de imagen (.jpg, .jpeg, .png)
     * @param vendedorId campo adicional del form-data
     */
    @Multipart
    @POST("api/productos/{idProducto}/imagenes")
    suspend fun agregarImagen(
        @Path("idProducto") idProducto: Long,
        @Part file: MultipartBody.Part,
        @Part("vendedorId") vendedorId: RequestBody
    ): Response<ProductoResponse>
}
