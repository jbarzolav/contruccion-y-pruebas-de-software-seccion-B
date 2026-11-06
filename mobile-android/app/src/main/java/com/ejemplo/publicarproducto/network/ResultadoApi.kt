package com.ejemplo.publicarproducto.network

import com.ejemplo.publicarproducto.model.ErrorResponse
import com.google.gson.Gson
import retrofit2.Response

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
