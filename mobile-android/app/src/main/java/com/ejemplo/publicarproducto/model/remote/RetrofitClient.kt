package com.ejemplo.publicarproducto.model.remote

import com.ejemplo.publicarproducto.BuildConfig
import okhttp3.OkHttpClient
import com.ejemplo.publicarproducto.model.session.NotificadorErroresAutorizacion
import com.ejemplo.publicarproducto.model.session.SessionManager
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

    // AUD-33: la URL se configura por variante en BuildConfig.
    private val BASE_URL: String = BuildConfig.BASE_URL
    private const val TIMEOUT_SEGUNDOS = 15L

    // HU 23 - Token compartido; nunca se registra Authorization ni contraseñas.
    private var sesion: SessionManager? = null
    private var onErrorAutorizacion: ((Int) -> Unit)? = null

    /**
     * Configura la sesión que usa el interceptor para el Bearer.
     *
     * El manejador de errores vive en Model ([NotificadorErroresAutorizacion]):
     * la capa de presentación lo consume desde su ViewModel, de modo que ni la
     * Activity ni las pantallas participan del flujo HTTP.
     */
    fun configurarSesion(
        manager: SessionManager,
        onError: (Int) -> Unit = NotificadorErroresAutorizacion::notificar
    ) {
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
                val ruta = original.url.encodedPath
                val publica = PoliticaErroresAutorizacion.esRutaPublica(ruta)
                // Un token vacío equivale a "sin token": no se manda cabecera
                // `Authorization` ni se considera una sesión vigente.
                val token = if (publica) null else sesion?.obtenerToken()?.takeIf { it.isNotBlank() }
                val request = original.newBuilder().apply {
                    if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                }.build()
                val respuesta = chain.proceed(request)
                // R4 - La regla de "la sesión no cambió" se conserva: si el
                // token cambió mientras la petición viajaba, la respuesta
                // pertenece a otra sesión y no se invalida la actual. Además,
                // un 401 con el token ya a null también se notifica, porque
                // el backend rechazó la petición.
                val codigo = PoliticaErroresAutorizacion.codigoANotificar(
                    ruta = ruta,
                    tokenEnviado = token,
                    tokenVigente = sesion?.obtenerToken()?.takeIf { it.isNotBlank() },
                    codigo = respuesta.code
                )
                when (codigo) {
                    401 -> onErrorAutorizacion?.invoke(401)
                    403 -> {
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
    val checkoutApi: CheckoutApi by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(httpClientSinLog)
            .addConverterFactory(GsonConverterFactory.create()).build().create(CheckoutApi::class.java)
    }
}
