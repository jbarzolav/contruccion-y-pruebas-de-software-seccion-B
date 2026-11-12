package com.ejemplo.publicarproducto.model.sync

import android.content.Context
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Observador de conectividad de la aplicación.
 *
 * Cuando se recupera la red se comprueba si el usuario tiene operaciones
 * pendientes y, en ese caso, se programa el trabajo de sincronización.
 * La detección se apoya en WorkManager (`NetworkType.CONNECTED`), que además
 * reintenta con backoff cuando el servidor no responde.
 *
 * R9 - El observador tiene una ruta explícita de parada.
 *
 * Antes el `CoroutineScope` era un scope de proceso sin ningún mecanismo de
 * cancelación. Ahora el alcance se guarda en una variable, se puede cancelar
 * con [detener] y `PulgaTecApplication.onTerminate()` lo invoca. Sigue sin
 * haber un `GlobalScope`: el trabajo pertenece a la aplicación y muere con
 * ella.
 */
object ObservadorConectividad {

    /** Alcance del observador; se cancela por completo en [detener]. */
    @Volatile
    private var alcance: CoroutineScope? = null

    @Volatile
    private var iniciado = false

    /** Arranca la observación de la red (una sola vez por proceso). */
    fun iniciar(context: Context) {
        if (iniciado) return
        iniciado = true
        val app = context.applicationContext
        // El contenedor se resuelve de forma síncrona: así la observación no
        // depende de cuándo se ejecute la corriente ni de que las pruebas
        // liberen el contenedor mientras tanto.
        val contenedor = ProveedorDatos.inicializar(app)
        val trabajo = SupervisorJob()
        val nuevoAlcance = CoroutineScope(trabajo + Dispatchers.Default)
        alcance = nuevoAlcance
        nuevoAlcance.launch {
            contenedor.conectividad.observar().collectLatest { hayConexion ->
                if (!hayConexion) return@collectLatest
                val usuarioId = contenedor.sesion.obtenerUsuarioId()
                if (usuarioId <= 0L) return@collectLatest
                val hayPendientes = contenedor.cola.listarPendientes(usuarioId).isNotEmpty()
                if (hayPendientes) ProgramadorSincronizacion.programar(app, usuarioId)
            }
        }
    }

    /**
     * R9 - Detiene la observación de conectividad.
     *
     * Cancela el trabajo en curso y deja el observador listo para volver a
     * arrancarse. Lo invoca `PulgaTecApplication.onTerminate()`.
     */
    fun detener() {
        alcance?.cancel()
        alcance = null
        iniciado = false
    }

    /** `true` mientras el observador esté activo (lo usan las pruebas). */
    fun activo(): Boolean = alcance != null
}
