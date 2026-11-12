package com.ejemplo.publicarproducto.testing

import com.ejemplo.publicarproducto.model.remote.AdminApi
import com.ejemplo.publicarproducto.model.remote.AuthApi
import com.ejemplo.publicarproducto.model.remote.ConversacionApi
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Servidor HTTP de pruebas.
 *
 * Levanta un servidor local y expone las interfaces reales de Retrofit
 * (`ProductoApi`, `AuthApi` y `AdminApi`) contra él. Solo se usa en las
 * pruebas instrumentadas: la aplicación usa siempre el backend real y sus
 * endpoints reales.
 */
class ServidorDePruebas {

    val servidor = MockWebServer()

    val api: ProductoApi
    val authApi: AuthApi
    val adminApi: AdminApi
    // HU 38/HU 39 - conversaciones: misma instancia de Retrofit que los
    // demás servicios, apuntando siempre al servidor de pruebas.
    val conversacionApi: ConversacionApi

    init {
        servidor.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(servidor.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(ProductoApi::class.java)
        authApi = retrofit.create(AuthApi::class.java)
        adminApi = retrofit.create(AdminApi::class.java)
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

    /**
     * Peticiones recibidas, en orden de llegada.
     *
     * `MockWebServer.takeRequest()` devuelve la petición MÁS ANTIGUA de la
     * cola y **se queda bloqueado** si la cola está vacía. Por eso todas las
     * lecturas pasan por esta caché: `drenar()` solo llama a `takeRequest()`
     * cuando el servidor ha registrado más peticiones de las ya cacheadas,
     * de modo que nunca puede bloquearse esperando una petición que no
     * llegará.
     */
    private val recibidas = mutableListOf<RecordedRequest>()

    /**
     * Cuántas peticiones se han consumido ya con [ultimaRuta] y
     * [ultimaPeticion]. Estas devuelven la **siguiente pendiente de lectura**
     * en orden de llegada (FIFO), que es la semántica que las pruebas
     * existentes esperan cuando una pantalla encadena dos llamadas.
     */
    private var consumidas = 0

    /** Vuelca en la caché las peticiones nuevas. Nunca bloquea. */
    private fun drenar() {
        while (servidor.requestCount > recibidas.size) {
            recibidas.add(servidor.takeRequest())
        }
    }

    /**
     * Ruta de la siguiente petición pendiente de lectura (orden de llegada).
     *
     * Si no llegó ninguna petición, falla con un diagnóstico explícito en
     * lugar de quedarse bloqueado para siempre.
     */
    fun ultimaRuta(): String = siguientePeticion().path.orEmpty()

    /**
     * Siguiente petición pendiente de lectura (ruta, método, cabeceras y
     * cuerpo). Útil para verificar que los contratos HTTP no cambian.
     */
    fun ultimaPeticion(): RecordedRequest = siguientePeticion()

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

    /**
     * Ruta de la PRIMERA petición recibida, sin consumirla.
     * No bloquea. Útil cuando el test lanza varias peticiones en paralelo.
     */
    fun primeraRuta(): String? {
        drenar()
        return recibidas.firstOrNull()?.path
    }

    /**
     * Ruta de la ÚLTIMA petición recibida, sin consumirla.
     * No bloquea. Es la forma inequívoca de leer "la última".
     */
    fun ultimaRutaRecibida(): String? {
        drenar()
        return recibidas.lastOrNull()?.path
    }

    /**
     * Última petición recibida completa, sin consumirla.
     * No bloquea.
     */
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

    /**
     * TODAS las peticiones recibidas (ruta, método, cabeceras y cuerpo), en
     * orden de llegada. No bloquea: solo lee las peticiones ya recibidas.
     *
     * Cuando una pantalla dispara varias peticiones en paralelo, hay que
     * buscar la que interesa en esta lista.
     */
    fun peticiones(): List<RecordedRequest> {
        drenar()
        return recibidas.drop(consumidas).toList()
    }

    /** Número de peticiones recibidas. */
    fun peticionesRecibidas(): Int {
        drenar()
        return recibidas.size
    }

    /** Ruta de la petición del carrito (POST /api/carrito/items), si llegó. */
    fun peticionCarrito(): RecordedRequest? =
        peticiones().firstOrNull { it.path == "/api/carrito/items" }

    /** Simula que el servidor no está disponible. */
    fun apagar() {
        servidor.shutdown()
    }

    fun cerrar() {
        runCatching { servidor.shutdown() }
    }
}
