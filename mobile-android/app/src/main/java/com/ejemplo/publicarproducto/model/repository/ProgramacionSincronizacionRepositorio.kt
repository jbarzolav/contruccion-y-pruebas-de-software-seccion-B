package com.ejemplo.publicarproducto.model.repository

import android.content.Context
import com.ejemplo.publicarproducto.model.sync.ProgramadorSincronizacion

/**
 * Encapsula la solicitud de sincronización para que la capa de presentación
 * no dependa de WorkManager ni de su API de programación.
 *
 * No modifica la cola ni las políticas del trabajo: delega en el programador
 * existente, que conserva trabajo único, conectividad requerida y reintentos.
 */
class ProgramacionSincronizacionRepositorio(
    contexto: Context,
    private val cola: ColaOperacionesRepositorio
) {
    private val contextoAplicacion = contexto.applicationContext

    fun programar(usuarioId: Long) {
        ProgramadorSincronizacion.programar(contextoAplicacion, usuarioId)
    }

    fun cancelar(usuarioId: Long) {
        ProgramadorSincronizacion.cancelar(contextoAplicacion, usuarioId)
    }

    /** Programa el trabajo de inicio de sesión solo si la cola del usuario no está vacía. */
    suspend fun programarSiHayPendientes(usuarioId: Long) {
        if (cola.listarPendientes(usuarioId).isNotEmpty()) programar(usuarioId)
    }
}
