package com.ejemplo.publicarproducto.model.remote

import com.ejemplo.publicarproducto.model.RecuperacionRequest
import com.ejemplo.publicarproducto.model.CodigoRecuperacionRequest
import com.ejemplo.publicarproducto.model.ValidacionCodigoResponse
import com.ejemplo.publicarproducto.model.RestablecerRequest
import com.ejemplo.publicarproducto.model.RecuperacionResponse
import com.ejemplo.publicarproducto.model.LoginRequest
import com.ejemplo.publicarproducto.model.LoginResponse
import com.ejemplo.publicarproducto.model.RegistroRequest
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.model.ActualizarPerfilRequest
import com.ejemplo.publicarproducto.model.PerfilUsuarioResponse
import com.ejemplo.publicarproducto.model.PerfilVendedorRequest
import com.ejemplo.publicarproducto.model.PerfilVendedorResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
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

    // HU 22 - Solicitud del código de recuperación por correo.
    @POST("api/auth/recuperacion")
    suspend fun solicitarRecuperacion(@Body request: RecuperacionRequest): Response<RecuperacionResponse>

    // HU 22 - Verificación del código de 6 dígitos (paso intermedio del flujo
    // web /restablecer). 200: {valido, mensaje}; 400: código inválido o
    // vencido {status, error, messages}; 429: demasiados intentos.
    // El token del enlace del correo es este mismo código, así que el
    // deep link pulgatec://restablecer?token=... también pasa por aquí.
    @POST("api/auth/recuperacion/verificar")
    suspend fun verificarCodigoRecuperacion(@Body request: CodigoRecuperacionRequest): Response<ValidacionCodigoResponse>

    // HU 22 - Nueva contraseña; el campo token es el código ya verificado.
    @POST("api/auth/restablecer")
    suspend fun restablecer(@Body request: RestablecerRequest): Response<RecuperacionResponse>

    // Identidad/rol de la sesión siguen siendo responsabilidad del backend user Spring.
    @GET("api/auth/me")
    suspend fun obtenerSesion(): Response<UsuarioRolResponse>

    // HU 27 / HU 28 - Perfil del usuario autenticado (incluye nombre y teléfono).
    @GET("api/usuarios/me")
    suspend fun obtenerPerfil(): Response<PerfilUsuarioResponse>

    // HU 28 - Edición de datos personales del usuario autenticado.
    // 200: perfil actualizado; 400: datos inválidos; 401: sesión vencida;
    // 409: correo ya registrado por otra cuenta. La identidad viene del Bearer.
    @PATCH("api/usuarios/me")
    suspend fun actualizarPerfil(@Body request: ActualizarPerfilRequest): Response<PerfilUsuarioResponse>

    // HU 30 - Foto JPG/PNG multipart; la identidad viene del Bearer vigente.
    @Multipart
    @POST("api/usuarios/me/foto")
    suspend fun actualizarFotoPerfil(@Part file: okhttp3.MultipartBody.Part): Response<PerfilUsuarioResponse>

    // HU 29 - Perfil comercial del vendedor autenticado (solo rol VENDEDOR;
    // CLIENTE recibe 403 desde el backend). GET devuelve campos vacíos si
    // aún no se ha completado.
    @GET("api/vendedores/me/perfil")
    suspend fun obtenerPerfilVendedor(): Response<PerfilVendedorResponse>

    @PATCH("api/vendedores/me/perfil")
    suspend fun guardarPerfilVendedor(@Body request: PerfilVendedorRequest): Response<PerfilVendedorResponse>

    // HU25 - UserApiClient agrega el Bearer existente mediante SessionManager.
    @POST("api/auth/logout")
    suspend fun cerrarSesion(): Response<AuthMessageResponse>
}
