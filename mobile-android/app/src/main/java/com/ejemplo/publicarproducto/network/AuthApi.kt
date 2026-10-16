package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.RecuperacionRequest
import com.ejemplo.publicarproducto.model.RestablecerRequest
import com.ejemplo.publicarproducto.model.RecuperacionResponse
import com.ejemplo.publicarproducto.model.LoginRequest
import com.ejemplo.publicarproducto.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.model.AsignarRolRequest
import com.ejemplo.publicarproducto.model.PaginaAuditoriaResponse
import retrofit2.http.Query

interface AuthApi {

    // HU 21 - POST /api/auth/login
    // 200: {token, id, correo}
    // 400: validaciones del backend {status, error, messages}
    // 401: credenciales inválidas (mismo mensaje para ambos casos)
    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>
    // HU 22 - Recuperación mediante enlace temporal de un solo uso.
    @POST("api/auth/recuperacion")
    suspend fun solicitarRecuperacion(@Body request: RecuperacionRequest): Response<RecuperacionResponse>

    @POST("api/auth/restablecer")
    suspend fun restablecer(@Body request: RestablecerRequest): Response<RecuperacionResponse>

    // HU 23 - Sesión actual y administración de roles.
    @GET("api/auth/me")
    suspend fun obtenerSesion(): Response<UsuarioRolResponse>
    @GET("api/admin/usuarios")
    suspend fun listarUsuarios(): Response<List<UsuarioRolResponse>>

    // HU24 - Historial administrativo paginado.
    @GET("api/admin/auditoria")
    suspend fun listarAuditoria(@Query("page") pagina: Int, @Query("size") tamano: Int): Response<PaginaAuditoriaResponse>

    @PATCH("api/admin/usuarios/{idUsuario}/rol")
    suspend fun asignarRol(@Path("idUsuario") idUsuario: Long, @Body request: AsignarRolRequest): Response<UsuarioRolResponse>
}
