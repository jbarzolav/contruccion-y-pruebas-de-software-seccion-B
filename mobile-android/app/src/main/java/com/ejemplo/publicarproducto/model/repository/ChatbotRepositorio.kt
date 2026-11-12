package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.ChatbotRequest
import com.ejemplo.publicarproducto.model.ChatbotResponse
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.remote.ejecutarPeticion

/**
 * Repositorio del chatbot (HU 16 - recomendaciones y HU 17 - consultas
 * técnicas sobre componentes electrónicos).
 *
 * Es el ÚNICO punto de la app que habla con los endpoints de chatbot: la
 * pantalla y el ViewModel ya no conocen `ProductoApi`, Retrofit ni Gson.
 *
 * Reglas conservadas de la implementación anterior:
 * - Los textos de error son exactamente los que mostraba la interfaz antes
 *   de la refactorización:
 *     · sin `messages[]` -> "Error N: no se pudo consultar."
 *     · con `messages[]` -> el PRIMER mensaje de la lista.
 *     · sin conexión -> "No hay conexión con el backend (10.0.2.2:9000): …"
 *     · 2xx sin cuerpo -> "Error N: no se pudo consultar." (igual que antes).
 * - "No hay una respuesta disponible." lo decide el ViewModel al pintar la
 *   respuesta: es texto de presentación, no de datos.
 * - Una cancelación de coroutine NO es un error de red: se vuelve a lanzar
 *   para no convertirla en un `ResultadoApi.Error`.
 *
 * El chatbot es una operación EN LÍNEA: no hay entidad en Room, no se
 * encola ninguna operación y no interviene WorkManager. El comportamiento
 * Offline First del resto de la app no se toca.
 */
class ChatbotRepositorio(
    private val api: ProductoApi,
) {

    companion object {
        /** Texto de red que ya mostraba la pantalla del chatbot. */
        const val SIN_CONEXION = "No hay conexión con el backend (10.0.2.2:9000): "

        /** Texto cuando el backend no envía `messages[]` ("Error N: …"). */
        const val SIN_MENSAJES = "no se pudo consultar."
    }

    /** El chatbot muestra solo el primer mensaje de `messages[]`. */
    private val primerMensaje: (List<String>) -> String = { lista -> lista.firstOrNull().orEmpty() }

    /** Texto por código HTTP, idéntico al que ya construía la interfaz. */
    private val porCodigo: (Int) -> String = { codigo -> "Error $codigo: $SIN_MENSAJES" }

    // ------------------------------------------------------------------ HU 16
    /**
     * Recomienda productos para la consulta del usuario
     * (`POST /api/chatbot/recomendaciones`).
     */
    suspend fun recomendarProductos(consulta: String): ResultadoApi<ChatbotResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = porCodigo,
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) { api.recomendarProductos(ChatbotRequest(consulta = consulta)) }

    // ------------------------------------------------------------------ HU 17
    /**
     * Responde una consulta técnica sobre componentes electrónicos
     * (`POST /api/chatbot/consultas-tecnicas`).
     */
    suspend fun consultarComponenteTecnico(consulta: String): ResultadoApi<ChatbotResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = porCodigo,
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) { api.consultarComponenteTecnico(ChatbotRequest(consulta = consulta)) }
}
