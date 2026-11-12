package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.ConfiguracionConsultas
import com.ejemplo.publicarproducto.model.remote.ConsultaRequest
import com.ejemplo.publicarproducto.model.remote.ConversacionApi
import com.ejemplo.publicarproducto.model.remote.ConversacionResponse
import com.ejemplo.publicarproducto.model.remote.ConversacionResumenResponse
import com.ejemplo.publicarproducto.model.remote.MensajeConsulta
import com.ejemplo.publicarproducto.model.remote.PaginaMensajesResponse
import com.ejemplo.publicarproducto.model.remote.RespuestaConsultaRequest
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.remote.ejecutarPeticion

/**
 * Repositorio de consultas y conversaciones (HU 38 y HU 39).
 *
 * Es el ÚNICO punto de la app que habla con `ConversacionApi`: la pantalla y
 * el ViewModel ya no conocen Retrofit ni Gson. Se usa el cliente existente
 * `UserApiClient.conversacionApi`, sin crear ninguno nuevo.
 *
 * Reglas conservadas de la implementación anterior:
 * - Sin `messages[]` -> "Error N. Intenta nuevamente."
 * - Con `messages[]` -> el PRIMER mensaje de la lista.
 * - Sin conexión -> el mensaje CRUDO de la excepción (`e.message`), SIN
 *   prefijos ni textos añadidos.
 * - El backend devuelve los mensajes en orden DESCENDENTE: este repositorio
 *   los expone en ORDEN CRONOLÓGICO (ascendente), aplicando `reversed()`
 *   UNA sola vez. La acumulación de páginas se hace con [acumularMensajes].
 *
 * Es una funcionalidad EN LÍNEA: no hay tabla ni entidad en Room, no se
 * encola ninguna operación y no interviene WorkManager. El comportamiento
 * Offline First del resto de la app no se toca.
 */
class ConversacionesRepositorio(
    private val api: ConversacionApi,
) {

    companion object {
        /** Texto cuando el backend no envía `messages[]`. */
        const val SIN_MENSAJES = "Intenta nuevamente."
    }

    /** Texto por código HTTP, idéntico al que ya construía el ViewModel. */
    private val porCodigo: (Int) -> String = { codigo -> "Error $codigo. $SIN_MENSAJES" }

    /** El chatbot y las consultas muestran solo el primer mensaje. */
    private val primerMensaje: (List<String>) -> String = { lista -> lista.firstOrNull().orEmpty() }

    // ------------------------------------------------------------------ HU 38
    /** Configuración de las consultas (longitud máxima del mensaje). */
    suspend fun configuracion(): ResultadoApi<ConfiguracionConsultas> =
        ejecutarPeticion(
            excepcion = { e -> e.message ?: "No se pudieron cargar las consultas." },
            porCodigo = porCodigo,
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) { api.configuracion() }

    /** Lista de conversaciones del usuario (`GET /api/conversaciones`). */
    suspend fun listarConversaciones(): ResultadoApi<List<ConversacionResumenResponse>> =
        ejecutarPeticion(
            excepcion = { e -> e.message ?: "No se pudieron cargar las consultas." },
            porCodigo = porCodigo,
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) { api.listar() }

    /**
     * Mensajes de una conversación, YA EN ORDEN CRONOLÓGICO.
     *
     * El backend los envía descendentes; aquí se invierten una sola vez.
     * Devuelve también `pagina` y `tieneSiguiente` tal cual los calcula el
     * servidor, para que la pantalla pueda pedir la página siguiente.
     */
    suspend fun obtenerMensajes(
        idConversacion: Long,
        pagina: Int = 0,
    ): ResultadoApi<PaginaMensajesResponse> {
        val resultado = ejecutarPeticion(
            excepcion = { e -> e.message ?: "No se pudo cargar el historial." },
            porCodigo = porCodigo,
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) { api.mensajes(idConversacion, pagina) }

        return when (resultado) {
            is ResultadoApi.Exito -> ResultadoApi.Exito(
                resultado.valor.copy(mensajes = resultado.valor.mensajes.reversed())
            )

            is ResultadoApi.Error -> resultado
        }
    }

    /**
     * Acumula una página sobre los mensajes ya cargados.
     *
     * - Página 0: REEMPLAZA (es el inicio de la conversación).
     * - Página anterior: se ANTEPONE, de modo que la lista sigue en orden
     *   cronológico (los más antiguos primero).
     *
     * Cada página es un rango distinto del backend, por lo que no se
     * duplican identificadores: la misma regla que ya usaba el ViewModel.
     */
    fun acumularMensajes(
        pagina: Int,
        mensajesPagina: List<MensajeConsulta>,
        actuales: List<MensajeConsulta>,
    ): List<MensajeConsulta> = if (pagina == 0) mensajesPagina else mensajesPagina + actuales

    /**
     * HU 36 - El cliente abre una consulta sobre un producto
     * (`POST /api/conversaciones`).
     */
    suspend fun consultarSobreProducto(
        productoId: Long,
        mensaje: String,
    ): ResultadoApi<ConversacionResponse> =
        ejecutarPeticion(
            excepcion = { e -> e.message ?: "No se pudo enviar el mensaje." },
            porCodigo = porCodigo,
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) { api.consultar(ConsultaRequest(productoId = productoId, mensaje = mensaje)) }

    // ------------------------------------------------------------------ HU 39
    /** HU 39 - Responde una conversación (`POST /api/conversaciones/{id}/mensajes`). */
    suspend fun responderConsulta(
        idConversacion: Long,
        mensaje: String,
    ): ResultadoApi<MensajeConsulta> =
        ejecutarPeticion(
            excepcion = { e -> e.message ?: "No se pudo enviar el mensaje." },
            porCodigo = porCodigo,
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) { api.responder(idConversacion, RespuestaConsultaRequest(mensaje = mensaje)) }
}
