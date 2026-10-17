package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.RecuperacionRequest
import com.ejemplo.publicarproducto.model.RestablecerRequest
import com.ejemplo.publicarproducto.model.RecuperacionResponse
import com.ejemplo.publicarproducto.model.LoginRequest
import com.ejemplo.publicarproducto.model.LoginResponse
import com.ejemplo.publicarproducto.model.RegistroRequest
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import com.ejemplo.publicarproducto.model.AuthMessageResponse

interface AuthApi {

    // HU 21 - POST /api/auth/login
    // 200: {token, id, correo}
    // 400: validaciones del backend {status, error, messages}
    // 401: credenciales inválidas (mismo mensaje para ambos casos)
    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    // HU 26 - POST /api/auth/registro
    // 201: {mensaje} sin token automático; 409 si el correo ya está registrado.
    @POST("api/auth/registro")
    suspend fun registro(@Body request: RegistroRequest): Response<AuthMessageResponse>

    // HU 22 - Recuperación mediante enlace temporal de un solo uso.
    @POST("api/auth/recuperacion")
    suspend fun solicitarRecuperacion(@Body request: RecuperacionRequest): Response<RecuperacionResponse>

    @POST("api/auth/restablecer")
    suspend fun restablecer(@Body request: RestablecerRequest): Response<RecuperacionResponse>

    // Identidad/rol de la sesión siguen siendo responsabilidad del backend user Spring.
    @GET("api/auth/me")
    suspend fun obtenerSesion(): Response<UsuarioRolResponse>

    // HU25 - UserApiClient agrega el Bearer existente mediante SessionManager.
    @POST("api/auth/logout")
    suspend fun cerrarSesion(): Response<AuthMessageResponse>
}
