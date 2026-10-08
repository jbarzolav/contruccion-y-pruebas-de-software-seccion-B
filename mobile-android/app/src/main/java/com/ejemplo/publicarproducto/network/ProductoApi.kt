package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.CarritoTotalResponse
import com.ejemplo.publicarproducto.model.DisponibilidadProductoResponse
import com.ejemplo.publicarproducto.model.EstadoProductoRequest
import com.ejemplo.publicarproducto.model.ItemCarritoCantidadRequest
import com.ejemplo.publicarproducto.model.ItemCarritoRequest
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    // HU 05 - GET /api/vendedores/me/productos?vendedorId=1
    @GET("api/vendedores/me/productos")
    suspend fun obtenerMisProductos(
        @Query("vendedorId") vendedorId: Long
    ): Response<List<ProductoResponse>>

    // HU 06 / HU 09 - GET /api/productos?nombre={texto}&sort={criterio}
    @GET("api/productos")
    suspend fun buscarProductosPorNombre(
        @Query("nombre") nombre: String,
        @Query("sort") sort: String? = null
    ): Response<List<ProductoResponse>>

    // HU 10 - GET /api/productos/{idProducto}/disponibilidad
    @GET("api/productos/{idProducto}/disponibilidad")
    suspend fun obtenerDisponibilidadProducto(
        @Path("idProducto") idProducto: Long
    ): Response<DisponibilidadProductoResponse>

    // HU 07 - GET /api/productos/catalogo (solo productos DISPONIBLE)
    @GET("api/productos/catalogo")
    suspend fun listarCatalogo(): Response<List<ProductoResponse>>

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

    // HU 11 - POST /api/carrito/items  { productoId, cantidad } → 201 (400 si excede stock)
    @POST("api/carrito/items")
    suspend fun agregarAlCarrito(
        @Body request: ItemCarritoRequest
    ): Response<ItemCarritoResponse>

    // HU 12 - PUT /api/carrito/items/{idItemCarrito}  { cantidad } → 200 subtotal recalculado
    @PUT("api/carrito/items/{idItemCarrito}")
    suspend fun actualizarCantidadItem(
        @Path("idItemCarrito") idItemCarrito: Long,
        @Body request: ItemCarritoCantidadRequest
    ): Response<ItemCarritoResponse>

    // HU 12 - GET /api/carrito/items → lista de ítems con sus subtotales
    @GET("api/carrito/items")
    suspend fun obtenerCarrito(): Response<List<ItemCarritoResponse>>

    // HU 13 - DELETE /api/carrito/items/{idItemCarrito}
    @DELETE("api/carrito/items/{idItemCarrito}")
    suspend fun eliminarItemCarrito(
        @Path("idItemCarrito") idItemCarrito: Long
    ): Response<Void>


    // HU 14 - GET /api/carrito/total
    @GET("api/carrito/total")
    suspend fun obtenerTotalCarrito(): Response<CarritoTotalResponse>

}
