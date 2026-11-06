package com.ejemplo.publicarproducto.sync

import android.content.Context
import com.ejemplo.publicarproducto.data.ProveedorDatos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Observador de conectividad de la aplicación.
 *
 * Cuando se recupera la red se comprueba si el usuario tiene operaciones
 * pendientes y, en ese caso, se programa el trabajo de sincronización.
 * La detección se apoya en WorkManager (`NetworkType.CONNECTED`), que además
 * reintenta con backoff cuando el servidor no responde.
 */
object ObservadorConectividad {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var iniciado = false

    /** Arranca la observación de la red (una sola vez por proceso). */
    fun iniciar(context: Context) {
        if (iniciado) return
        iniciado = true
        val app = context.applicationContext
        alcance.launch {
            val contenedor = ProveedorDatos.inicializar(app)
            contenedor.conectividad.observar().collectLatest { hayConexion ->
                if (!hayConexion) return@collectLatest
                val usuarioId = contenedor.sesion.obtenerUsuarioId()
                if (usuarioId <= 0L) return@collectLatest
                val hayPendientes = contenedor.cola.listarPendientes(usuarioId).isNotEmpty()
                if (hayPendientes) ProgramadorSincronizacion.programar(app, usuarioId)
            }
        }
    }
}
