package com.ejemplo.publicarproducto.model.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import kotlinx.coroutines.CancellationException

/**
 * Trabajador de sincronización (WorkManager).
 *
 * - Usa el cliente Retrofit existente y el Bearer de la sesión vigente.
 * - Procesa la cola pendiente en orden.
 * - Ante un error temporal devuelve `Result.retry()` (con backoff exponencial).
 * - Ante un rechazo definitivo del servidor no reintenta.
 * - Ante una sesión expirada detiene la sincronización (las operaciones quedan
 *   esperando a que el usuario vuelva a entrar).
 * - AUD-02: ante un fallo inesperado (por ejemplo, cuando no hay contenedor
 *   inicializado o cualquier excepción no prevista del servicio) no deja
 *   escapar la excepción: pide un reintento. Así la operación de la cola
 *   persistente no se queda en EN_CURSO y se vuelve a intentar más tarde en
 *   lugar de perderse.
 */
class SincronizacionWorker(
    context: Context,
    parametros: WorkerParameters
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        val usuarioId = inputData.getLong(ProgramadorSincronizacion.CLAVE_USUARIO_ID, 0L)
        if (usuarioId <= 0L) return Result.failure()

        return try {
            val contenedor = ProveedorDatos.inicializar(applicationContext)
            when (contenedor.sincronizacion.sincronizar(usuarioId)) {
                is ResultadoSincronizacion.Exito -> Result.success()
                is ResultadoSincronizacion.Reintentar -> Result.retry()
                is ResultadoSincronizacion.SesionExpirada -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // AUD-02: la operación sigue en la cola; WorkManager debe volver
            // a intentarlo y no dejarla marcada permanentemente EN_CURSO.
            Result.retry()
        }
    }
}
