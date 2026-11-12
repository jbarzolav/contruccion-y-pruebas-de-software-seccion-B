package com.ejemplo.publicarproducto.model.remote

import com.ejemplo.publicarproducto.model.ErrorResponse
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException

/**
 * Resultado de una petición al backend con la clasificación que necesita la
 * cola de operaciones: un error temporal se reintenta, un rechazo del
 * servidor (validación o permiso) no se reintenta nunca.
 */
sealed class ResultadoApi<out T> {
    data class Exito<T>(val valor: T) : ResultadoApi<T>()
    data class Error(
        val codigo: Int?,
        val mensaje: String,
        val temporal: Boolean,
        val sesionExpirada: Boolean = false
    ) : ResultadoApi<Nothing>()
}

/**
 * Ejecuta una llamada Retrofit y traduce el resultado.
 *
 * - `codigo == null`: no hubo red (excepción) -> error temporal.
 * - 5xx / 408 / 429: error temporal -> la operación queda pendiente y se reintenta.
 * - 401: la sesión venció -> la operación se detiene (no se reintenta en bucle).
 * - 4xx restante: rechazo definitivo del servidor -> no se reintenta.
 */
suspend fun <T> ejecutarPeticion(bloque: suspend () -> Response<T>): ResultadoApi<T> = try {
    val respuesta = bloque()
    if (respuesta.isSuccessful) {
        ResultadoApi.Exito(requireNotNull(respuesta.body()) { "El servidor devolvió una respuesta vacía." })
    } else {
        val codigo = respuesta.code()
        ResultadoApi.Error(
            codigo = codigo,
            mensaje = mensajeDeError(respuesta),
            temporal = codigo >= 500 || codigo == 408 || codigo == 429,
            sesionExpirada = codigo == 401
        )
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ResultadoApi.Error(
        codigo = null,
        mensaje = e.message ?: "No hay conexión con el servidor.",
        temporal = true
    )
}

private fun mensajeDeError(respuesta: Response<*>): String = runCatching {
    Gson().fromJson(respuesta.errorBody()?.string(), ErrorResponse::class.java)
        ?.messages?.firstOrNull()
}.getOrNull() ?: "Error ${respuesta.code()} del servidor."

/**
 * Variante de `ejecutarPeticion` para los flujos de producto y favoritos,
 * donde la interfaz anterior mostraba el detalle de la excepción.
 *
 * - `excepcion`: texto exacto que ya se mostraba cuando la llamada fallaba
 *   sin respuesta HTTP (por ejemplo
 *   "No hay conexión con el backend (10.0.2.2:9000): ...").
 * - `porCodigo`: texto por código HTTP cuando el backend no envía `messages`.
 * - `leerMensajes`: decide si ese endpoint documenta `{status,error,messages}`
 *   y sus mensajes deben leerse del cuerpo de error.
 * - `formatearMensajes`: cómo se convierte la lista de `messages` en un texto.
 *   Por defecto se unen con " | " (productos, favoritos). El carrito usa solo
 *   el primer mensaje (`firstOrNull`), como ya mostraba la interfaz.
 * - `vacio`: valor a devolver cuando el backend responde 2xx sin cuerpo; los
 *   endpoints de lista devuelven así la lista vacía que antes se obtenía con
 *   `body().orEmpty()`.
 *
 * No altera ninguna de las otras dos sobrecargas: solo añade una forma más
 * de traducir la respuesta, con los mismos textos que antes.
 */
suspend fun <T> ejecutarPeticion(
    excepcion: (Throwable) -> String,
    porCodigo: (Int) -> String,
    leerMensajes: (Int) -> Boolean = { false },
    formatearMensajes: (List<String>) -> String = { it.joinToString(" | ") },
    vacio: ((Int) -> T)? = null,
    bloque: suspend () -> Response<T>
): ResultadoApi<T> = try {
    val respuesta = bloque()
    when {
        respuesta.isSuccessful && respuesta.body() != null ->
            ResultadoApi.Exito(requireNotNull(respuesta.body()))

        respuesta.isSuccessful && vacio != null ->
            ResultadoApi.Exito(vacio(respuesta.code()))

        respuesta.isSuccessful ->
            ResultadoApi.Error(
                codigo = respuesta.code(),
                mensaje = porCodigo(respuesta.code()),
                temporal = false,
                sesionExpirada = false
            )

        else -> {
            val codigo = respuesta.code()
            val mensaje = if (leerMensajes(codigo)) {
                // Un formateador que devuelva un texto vacío (por ejemplo una
                // lista sin mensajes) cae en el texto por código.
                val texto = mensajesDeError(respuesta)?.let(formatearMensajes)
                if (!texto.isNullOrBlank()) texto else porCodigo(codigo)
            } else {
                porCodigo(codigo)
            }
            ResultadoApi.Error(
                codigo = codigo,
                mensaje = mensaje,
                temporal = codigo >= 500 || codigo == 408 || codigo == 429,
                sesionExpirada = codigo == 401
            )
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ResultadoApi.Error(
        codigo = null,
        mensaje = excepcion(e),
        temporal = true
    )
}

/**
 * Lee `messages` del cuerpo `{status, error, messages}` que comparten Spring
 * Boot y Django. Devuelve `null` cuando el cuerpo no tiene ese formato.
 *
 * Se usa desde los repositorios de autenticación y administración para
 * conservar exactamente los mensajes que ya mostraba la interfaz antes de
 * la refactorización MVVM.
 */
fun mensajesDeError(respuesta: Response<*>): List<String>? = runCatching {
    Gson().fromJson(respuesta.errorBody()?.string(), ErrorResponse::class.java)
        ?.messages?.takeIf { it.isNotEmpty() }
}.getOrNull()

/**
 * Variante de `ejecutarPeticion` para los flujos que necesitan un mensaje
 * concreto por endpoint en lugar del genérico "Error X del servidor".
 *
 * - `sinRed`: `IOException` (el dispositivo no llegó al backend).
 * - `fallo`: excepción inesperada o 2xx sin cuerpo.
 * - `porCodigo`: texto cuando el backend no envía `messages`.
 * - `leerMensajes`: decide, por código, si ese endpoint sí documenta
 *   `{status,error,messages}` y debe leerse el cuerpo de error.
 *
 * El detalle técnico de la excepción nunca llega a la interfaz, igual que
 * en el código original de los ViewModels.
 */
suspend fun <T> ejecutarPeticion(
    sinRed: String,
    fallo: String,
    porCodigo: (Int) -> String,
    leerMensajes: (Int) -> Boolean = { false },
    bloque: suspend () -> Response<T>
): ResultadoApi<T> = try {
    val respuesta = bloque()
    when {
        respuesta.isSuccessful && respuesta.body() != null ->
            ResultadoApi.Exito(requireNotNull(respuesta.body()))

        respuesta.isSuccessful ->
            ResultadoApi.Error(
                codigo = null,
                mensaje = fallo,
                temporal = false,
                sesionExpirada = false
            )

        else -> {
            val codigo = respuesta.code()
            val mensaje = if (leerMensajes(codigo)) {
                mensajesDeError(respuesta)?.joinToString(" ") ?: porCodigo(codigo)
            } else {
                porCodigo(codigo)
            }
            ResultadoApi.Error(
                codigo = codigo,
                mensaje = mensaje,
                temporal = codigo >= 500 || codigo == 408 || codigo == 429,
                sesionExpirada = codigo == 401
            )
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    ResultadoApi.Error(codigo = null, mensaje = sinRed, temporal = true)
} catch (e: Exception) {
    ResultadoApi.Error(codigo = null, mensaje = fallo, temporal = false)
}
