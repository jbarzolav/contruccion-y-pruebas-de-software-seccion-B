package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.EstadoProductoRequest
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductoApi {

    // HU 01 - POST /api/productos
    @POST("api/productos")
    suspend fun crearProducto(
        @Body request: ProductoRequest
    ): Response<ProductoResponse>

    // HU 03 - GET /api/productos/{idProducto}
    @GET("api/productos/{idProducto}")
    suspend fun obtenerProducto(
        @Path("idProducto") idProducto: Long
    ): Response<ProductoResponse>

    // HU 03 - PUT /api/productos/{idProducto}?vendedorId=1
    @PUT("api/productos/{idProducto}")
    suspend fun actualizarProducto(
        @Path("idProducto") idProducto: Long,
        @Body request: ProductoRequest,
        @Query("vendedorId") vendedorId: Long
    ): Response<ProductoResponse>

    // HU 04 - PATCH /api/productos/{idProducto}/estado? vendedorId=1 (baja lógica)
    @PATCH("api/productos/{idProducto}/estado")
    suspend fun cambiarEstado(
        @Path("idProducto") idProducto: Long,
        @Body request: EstadoProductoRequest,
        @Query("vendedorId") vendedorId: Long
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
