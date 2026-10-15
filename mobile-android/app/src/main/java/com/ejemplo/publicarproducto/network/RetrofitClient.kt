package com.ejemplo.publicarproducto.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente Retrofit.
 *
 * IMPORTANTE: desde el emulador de Android, el localhost de la PC es 10.0.2.2
 * (http://10.0.2.2:9000). Desde un dispositivo físico se debe usar la IP de la LAN.
 */
object RetrofitClient {

    private const val BASE_URL = "http://10.0.2.2:9000/"
    private const val TIMEOUT_SEGUNDOS = 15L

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    // HU 21 - cliente SIN logging para autenticación: el body del login
    // contiene la contraseña y jamás debe aparecer en Logcat.
    private val httpClientSinLog: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .build()
    }

    val productoApi: ProductoApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
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
}
