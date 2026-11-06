package com.ejemplo.publicarproducto.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Programador de la sincronización.
 *
 * Se usa `enqueueUniqueWork` con un nombre único por usuario: así nunca se
 * ejecutan dos sincronizaciones simultáneas para el mismo usuario y la cola
 * se procesa en orden. El trabajo exige red (`NetworkType.CONNECTED`), de
 * modo que WorkManager lo ejecuta en cuanto se recupera la conectividad.
 */
object ProgramadorSincronizacion {

    /** Clave de los datos de entrada del trabajo. */
    const val CLAVE_USUARIO_ID = "usuario_id"

    /** Etiqueta común a todos los trabajos de sincronización. */
    const val ETIQUETA = "sincronizacion"

    private const val PREFIJO = "pulgatec-sincronizacion-"

    /** Nombre único del trabajo de un usuario. */
    fun nombreTrabajo(usuarioId: Long): String = "$PREFIJO$usuarioId"

    /**
     * Programa la sincronización del usuario. Si ya hay un trabajo pendiente
     * para ese usuario, el nuevo se encadena detrás (`APPEND_OR_REPLACE`):
     * nunca se pierde una operación y nunca se ejecutan dos a la vez.
     */
    fun programar(context: Context, usuarioId: Long) {
        if (usuarioId <= 0L) return
        val solicitud = OneTimeWorkRequestBuilder<SincronizacionWorker>()
            .setInputData(workDataOf(CLAVE_USUARIO_ID to usuarioId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ETIQUETA)
            .addTag(nombreTrabajo(usuarioId))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                nombreTrabajo(usuarioId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                solicitud
            )
    }

    /** Cancela los trabajos de sincronización del usuario (cierre de sesión). */
    fun cancelar(context: Context, usuarioId: Long) {
        if (usuarioId <= 0L) return
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(nombreTrabajo(usuarioId))
    }
}
