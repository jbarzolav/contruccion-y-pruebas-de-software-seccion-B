package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.RecursosSincronizacion
import com.ejemplo.publicarproducto.model.local.EstadosSincronizacion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Base de los ViewModels que consumen la copia local (Offline First).
 *
 * Aporta el estado común de la mejora técnica:
 * - `sinConexion`: indica que el dispositivo no tiene red;
 * - `pendientesSincronizacion`: operaciones guardadas en el dispositivo que
 *   todavía NO confirmó el servidor;
 * - `avisoSincronizacion` / `rechazoSincronizacion`: resultado de la última
 *   sincronización (completada o rechazada).
 *
 * Ninguna de estas piezas sustituye a la fuente oficial: el backend sigue
 * mandando y la interfaz solo informa lo que realmente confirmó.
 *
 * R2 - Identidad de usuario reactiva.
 *
 * Antes, `usuarioId` se capturaba una sola vez en el `init` y quedaba
 * congelado: si el usuario cerraba sesión y entraba otra cuenta en la misma
 * `Activity`, los collectors seguían observing los datos del usuario
 * anterior. Ahora la identidad llega de `SessionManager.usuarioIdFlow`, que
 * es la única fuente de verdad:
 *
 * 1. al cambiar de usuario se cancelan TODOS los collectors del usuario
 *    anterior (los de la base y los de la subclase);
 * 2. se limpia el estado de presentación del usuario anterior;
 * 3. se inician las observaciones del usuario nuevo.
 *
 * Las subclases llaman a [iniciarObservacionesDeUsuario] al final de su
 * `init`: de ese modo, cuando la identidad cambia, el ViewModel ya tiene
 * todas sus propiedades inicializadas y puede limpiar su estado y reiniciar
 * sus collectors con seguridad.
 */
abstract class BaseOfflineViewModel : ViewModel() {

    protected val contenedor = ProveedorDatos.actual

    /**
     * Identidad activa del usuario (R2).
     *
     * Se actualiza de forma reactiva desde la sesión; deja de ser una
     * captura congelada en el momento de la construcción.
     */
    protected var usuarioId by mutableStateOf(contenedor.sesion.obtenerUsuarioId())
        private set

    /** El dispositivo no tiene red: se muestran los últimos datos sincronizados. */
    var sinConexion by mutableStateOf(false)
        protected set

    /** Operaciones guardadas en el dispositivo que aún no confirmó el servidor. */
    var pendientesSincronizacion by mutableStateOf(0L)
        protected set

    /** Aviso de la última sincronización completada. */
    var avisoSincronizacion by mutableStateOf<String?>(null)
        protected set

    /** Mensaje de la última operación rechazada por el servidor. */
    var rechazoSincronizacion by mutableStateOf<String?>(null)
        protected set

    /** Observador de la identidad de usuario (R2). */
    private var observadorIdentidad: Job? = null

    /** Alcance de las observaciones ligadas al usuario activo (R2). */
    private var alcanceUsuario: CoroutineScope? = null

    init {
        viewModelScope.launch {
            contenedor.conectividad.observar().collect { hayConexion ->
                sinConexion = !hayConexion
            }
        }
    }

    /**
     * Arranca la observación reactiva de la identidad del usuario.
     *
     * La llaman las subclases al final de su `init`. Es idempotente: una
     * segunda llamada no duplica el observador.
     */
    protected fun iniciarObservacionesDeUsuario() {
        if (observadorIdentidad != null) return
        observadorIdentidad = viewModelScope.launch {
            // `usuarioIdFlow` es un `StateFlow`: ya entrega solo valores
            // distintos, así que no hace falta ningún operador adicional.
            contenedor.sesion.usuarioIdFlow
                .collect { nuevoUsuarioId -> activarUsuario(nuevoUsuarioId) }
        }
    }

    /**
     * Cambia la identidad activa y reinicia las observaciones asociadas.
     *
     * El orden importa: primero se cancela el alcance del usuario anterior,
     * después se limpia el estado de presentación y, por último, se
     * empiezan a observar los datos del usuario nuevo. Así nunca se mezclan
     * datos de dos cuentas ni se sigue consultando información protegida de
     * una sesión ya cerrada.
     */
    private fun activarUsuario(nuevoUsuarioId: Long) {
        val cambioDeUsuario = nuevoUsuarioId != usuarioId
        usuarioId = nuevoUsuarioId

        // 1) Se detienen los collectors del usuario anterior.
        alcanceUsuario?.cancel()
        alcanceUsuario = null

        // 2) Se limpia el estado de presentación del usuario anterior.
        if (cambioDeUsuario) {
            pendientesSincronizacion = 0L
            avisoSincronizacion = null
            rechazoSincronizacion = null
            limpiarEstadoDeUsuario()
        }

        // 3) Sin sesión no se consulta información protegida.
        if (nuevoUsuarioId <= 0L) return

        val trabajo = SupervisorJob(viewModelScope.coroutineContext[Job])
        val alcance = CoroutineScope(viewModelScope.coroutineContext + trabajo)
        alcanceUsuario = alcance
        alcance.launch {
            // Observaciones comunes de la base.
            launch {
                contenedor.cola.observarPendientes(nuevoUsuarioId).collect { pendientes ->
                    pendientesSincronizacion = pendientes.size.toLong()
                }
            }
            launch {
                contenedor.metadatosRepositorio
                    .observar(nuevoUsuarioId, RecursosSincronizacion.OPERACIONES)
                    .collect { metadato ->
                        if (metadato?.estado == EstadosSincronizacion.RECHAZADO) {
                            rechazoSincronizacion = metadato.mensaje
                            avisoSincronizacion = null
                        } else {
                            rechazoSincronizacion = null
                            avisoSincronizacion = metadato?.mensaje
                        }
                    }
            }
            // Observaciones propias de la subclase.
            observarUsuario(nuevoUsuarioId, alcance)
        }
    }

    /**
     * Observaciones de datos del usuario activo (R2).
     *
     * La subclase implementa aquí sus collectors (pedidos, avisos, métricas,
     * umbrales...) usando el [alcance] recibido, de modo que se cancelan
     * automáticamente cuando el usuario cambia o el ViewModel se destruye.
     *
     * La implementa la base con cuerpo vacío: no todas las subclases necesitan
     * observar datos locales.
     */
    protected open fun observarUsuario(usuarioId: Long, alcance: CoroutineScope) {}

    /**
     * Limpia el estado de presentación del usuario anterior (R2).
     *
     * La subclase sobrescribe este método para vaciar sus listas y mensajes:
     * así no se muestra información del usuario anterior durante la
     * transición ni después de cerrar sesión.
     */
    protected open fun limpiarEstadoDeUsuario() {}

    override fun onCleared() {
        // Los collectors del usuario se cancelan con el ViewModel.
        alcanceUsuario?.cancel()
        alcanceUsuario = null
        observadorIdentidad?.cancel()
        observadorIdentidad = null
        super.onCleared()
    }
}
