package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.AsignarRolRequest
import com.ejemplo.publicarproducto.model.PaginaAuditoriaResponse
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints HU23/HU24 servidos exclusivamente por Django REST Framework. */
interface AdminApi {
    @GET("api/admin/usuarios")
    suspend fun listarUsuarios(): Response<List<UsuarioRolResponse>>

    @PATCH("api/admin/usuarios/{idUsuario}/rol")
    suspend fun asignarRol(
        @Path("idUsuario") idUsuario: Long,
        @Body request: AsignarRolRequest,
    ): Response<UsuarioRolResponse>

    @GET("api/admin/auditoria")
    suspend fun listarAuditoria(
        @Query("page") pagina: Int,
        @Query("size") tamano: Int,
    ): Response<PaginaAuditoriaResponse>
}

/** Cliente admin distinto del cliente Spring; Authorization usa la sesión única compartida. */
object AdminApiClient {
    private const val BASE_URL = "http://10.0.2.2:8001/"

    val api: AdminApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(UserApiClient.clienteAutenticado())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AdminApi::class.java)
    }
}
