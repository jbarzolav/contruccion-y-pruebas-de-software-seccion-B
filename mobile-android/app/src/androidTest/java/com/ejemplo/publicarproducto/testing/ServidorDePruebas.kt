package com.ejemplo.publicarproducto.testing

import com.ejemplo.publicarproducto.network.ProductoApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Servidor HTTP de pruebas.
 *
 * Levanta un servidor local y expone un `ProductoApi` real de Retrofit contra
 * él. Solo se usa en las pruebas instrumentadas: la aplicación usa siempre el
 * backend real y sus endpoints reales.
 */
class ServidorDePruebas {

    val servidor = MockWebServer()

    val api: ProductoApi

    init {
        servidor.start()
        api = Retrofit.Builder()
            .baseUrl(servidor.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProductoApi::class.java)
    }

    /** Encola una respuesta HTTP con el código y el cuerpo indicados. */
    fun responder(codigo: Int, cuerpo: String) {
        servidor.enqueue(
            MockResponse()
                .setResponseCode(codigo)
                .setHeader("Content-Type", "application/json")
                .setBody(cuerpo)
        )
    }

    /** Encola una respuesta de error con el formato del backend. */
    fun responderError(codigo: Int, mensaje: String) {
        responder(
            codigo,
            """{"status":$codigo,"error":"Error","messages":["$mensaje"]}"""
        )
    }

    /** Ruta de la última petición recibida. */
    fun ultimaRuta(): String = servidor.takeRequest().path.orEmpty()

    /** Número de peticiones recibidas. */
    fun peticionesRecibidas(): Int = servidor.requestCount

    /** Simula que el servidor no está disponible. */
    fun apagar() {
        servidor.shutdown()
    }

    fun cerrar() {
        runCatching { servidor.shutdown() }
    }
}
