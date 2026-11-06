package com.ejemplo.publicarproducto.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ejemplo.publicarproducto.data.ProveedorDatos

/**
 * Trabajador de sincronización (WorkManager).
 *
 * - Usa el cliente Retrofit existente y el Bearer de la sesión vigente.
 * - Procesa la cola pendiente en orden.
 * - Ante un error temporal devuelve `Result.retry()` (con backoff exponencial).
 * - Ante un rechazo definitivo del servidor no reintenta.
 * - Ante una sesión expirada detiene la sincronización (las operaciones quedan
 *   esperando a que el usuario vuelva a entrar).
 */
class SincronizacionWorker(
    context: Context,
    parametros: WorkerParameters
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        val usuarioId = inputData.getLong(ProgramadorSincronizacion.CLAVE_USUARIO_ID, 0L)
        if (usuarioId <= 0L) return Result.failure()

        val contenedor = ProveedorDatos.inicializar(applicationContext)
        return when (contenedor.sincronizacion.sincronizar(usuarioId)) {
            is ResultadoSincronizacion.Exito -> Result.success()
            is ResultadoSincronizacion.Reintentar -> Result.retry()
            is ResultadoSincronizacion.SesionExpirada -> Result.success()
        }
    }
}
