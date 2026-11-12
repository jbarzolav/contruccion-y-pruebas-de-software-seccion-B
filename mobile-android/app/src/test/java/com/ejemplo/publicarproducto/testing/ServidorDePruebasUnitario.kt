package com.ejemplo.publicarproducto.testing

import com.ejemplo.publicarproducto.model.remote.ConversacionApi
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Servidor HTTP de pruebas para las pruebas unitarias de la JVM.
 *
 * Es el equivalente de `ServidorDePruebas` (que vive en `androidTest`) para
 * poder verificar los repositorios sin emulador. Expone la interfaz real de
 * Retrofit contra un servidor local: las rutas, las query y los cuerpos se
 * comprueban igual que en las pruebas instrumentadas.
 */
class ServidorDePruebasUnitario {

    val servidor = MockWebServer()

    val api: ProductoApi
    // HU 38/HU 39 - conversaciones: misma instancia de Retrofit.
    val conversacionApi: ConversacionApi

    init {
        servidor.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(servidor.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(ProductoApi::class.java)
        conversacionApi = retrofit.create(ConversacionApi::class.java)
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

    /** Ruta de la siguiente petición pendiente de lectura (orden de llegada). */
    fun ultimaRuta(): String = siguientePeticion().path.orEmpty()

    /** Siguiente petición pendiente de lectura (ruta, método, cabeceras y cuerpo). */
    fun ultimaPeticion(): RecordedRequest = siguientePeticion()

    /**
     * Peticiones recibidas, en orden de llegada.
     *
     * `MockWebServer.takeRequest()` devuelve la petición MÁS ANTIGUA de la
     * cola y **se queda bloqueado** si la cola está vacía. Por eso todas las
     * lecturas pasan por esta caché: `drenar()` solo llama a `takeRequest()`
     * cuando el servidor ha registrado más peticiones de las ya cacheadas,
     * de modo que nunca puede bloquearse.
     */
    private val recibidas = mutableListOf<RecordedRequest>()

    /** Cuántas peticiones se han consumido ya con [ultimaRuta]/[ultimaPeticion]. */
    private var consumidas = 0

    /** Vuelca en la caché las peticiones nuevas. Nunca bloquea. */
    private fun drenar() {
        while (servidor.requestCount > recibidas.size) {
            recibidas.add(servidor.takeRequest())
        }
    }

    private fun siguientePeticion(): RecordedRequest {
        drenar()
        if (consumidas >= recibidas.size) {
            throw AssertionError(
                "No hay ninguna petición pendiente de lectura: el servidor recibió " +
                    "${recibidas.size} peticiones y ya se habían leído $consumidas. " +
                    "Revisa que la respuesta esté encolada antes de la llamada."
            )
        }
        return recibidas[consumidas++]
    }

    /** Ruta de la PRIMERA petición recibida, sin consumirla. No bloquea. */
    fun primeraRuta(): String? {
        drenar()
        return recibidas.firstOrNull()?.path
    }

    /** Ruta de la ÚLTIMA petición recibida, sin consumirla. No bloquea. */
    fun ultimaRutaRecibida(): String? {
        drenar()
        return recibidas.lastOrNull()?.path
    }

    /** Última petición recibida completa, sin consumirla. No bloquea. */
    fun ultimaPeticionRecibida(): RecordedRequest? {
        drenar()
        return recibidas.lastOrNull()
    }

    /**
     * Rutas de TODAS las peticiones recibidas, en orden de llegada.
     * No bloquea: solo lee las peticiones que ya se han recibido.
     */
    fun rutasRecibidas(): List<String> {
        drenar()
        return recibidas.drop(consumidas).map { it.path.orEmpty() }
    }

    /** TODAS las peticiones recibidas, en orden de llegada. No bloquea. */
    fun peticiones(): List<RecordedRequest> {
        drenar()
        return recibidas.drop(consumidas).toList()
    }

    /** Número de peticiones recibidas. */
    fun peticionesRecibidas(): Int {
        drenar()
        return recibidas.size
    }

    /** Simula que el servidor no está disponible. */
    fun apagar() {
        servidor.shutdown()
    }

    fun cerrar() {
        runCatching { servidor.shutdown() }
    }
}
