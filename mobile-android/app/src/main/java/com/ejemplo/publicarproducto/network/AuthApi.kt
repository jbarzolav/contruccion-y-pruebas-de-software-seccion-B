package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.RecuperacionRequest
import com.ejemplo.publicarproducto.model.RestablecerRequest
import com.ejemplo.publicarproducto.model.RecuperacionResponse
import com.ejemplo.publicarproducto.model.LoginRequest
import com.ejemplo.publicarproducto.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

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
}
