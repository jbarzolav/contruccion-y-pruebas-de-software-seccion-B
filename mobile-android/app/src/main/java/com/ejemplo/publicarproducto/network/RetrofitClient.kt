package com.ejemplo.publicarproducto.network

import okhttp3.OkHttpClient
import com.ejemplo.publicarproducto.session.SessionManager
import com.google.gson.JsonParser
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente Retrofit.
 *
 * IMPORTANTE: desde el emulador de Android, el localhost de la PC es 10.0.2.2
 * (http://10.0.2.2:9000). Desde un dispositivo físico se debe usar la IP de la LAN.
 */
object UserApiClient {

    private const val BASE_URL = "http://10.0.2.2:9000/"
    private const val TIMEOUT_SEGUNDOS = 15L

    // HU 23 - Token compartido; nunca se registra Authorization ni contraseñas.
    private var sesion: SessionManager? = null
    private var onErrorAutorizacion: ((Int) -> Unit)? = null
    fun configurarSesion(manager: SessionManager, onError: (Int) -> Unit) {
        sesion = manager
        onErrorAutorizacion = onError
    }
    fun usuarioActualId(): Long = requireNotNull(sesion?.obtenerUsuarioId()?.takeIf { it > 0 }) {
        "Debes iniciar sesión."
    }

    // HU 21 - cliente SIN logging para autenticación: el body del login
    // contiene la contraseña y jamás debe aparecer en Logcat.
    private val httpClientSinLog: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val publica = original.url.encodedPath in setOf("/api/auth/login", "/api/auth/recuperacion", "/api/auth/restablecer")
                val token = if (publica) null else sesion?.obtenerToken()
                val request = original.newBuilder().apply {
                    if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                }.build()
                val respuesta = chain.proceed(request)
                if (token != null && token == sesion?.obtenerToken()) {
                    if (respuesta.code == 401) onErrorAutorizacion?.invoke(401)
                    else if (respuesta.code == 403) {
                        val error = runCatching {
                            JsonParser.parseString(respuesta.peekBody(4096).string()).asJsonObject.get("error")?.asString
                        }.getOrNull()
                        if (error == "Autorización") onErrorAutorizacion?.invoke(403)
                    }
                }
                respuesta
            }
            .build()
    }

    /** AdminApiClient comparte el mismo SessionManager y Bearer, cambia solo el host. */
    internal fun clienteAutenticado(): OkHttpClient = httpClientSinLog

    val productoApi: ProductoApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClientSinLog)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProductoApi::class.java)
    }

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClientSinLog)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    val conversacionApi: ConversacionApi by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(httpClientSinLog)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ConversacionApi::class.java)
    }
}
