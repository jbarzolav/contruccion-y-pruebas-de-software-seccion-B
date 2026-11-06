package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.data.ProveedorDatos
import com.ejemplo.publicarproducto.local.RecursosSincronizacion
import com.ejemplo.publicarproducto.local.EstadosSincronizacion
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
 */
abstract class BaseOfflineViewModel : ViewModel() {

    protected val contenedor = ProveedorDatos.actual
    protected val usuarioId = contenedor.sesion.obtenerUsuarioId()

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

    init {
        viewModelScope.launch {
            contenedor.conectividad.observar().collect { hayConexion ->
                sinConexion = !hayConexion
            }
        }
        if (usuarioId > 0L) {
            viewModelScope.launch {
                contenedor.cola.observarPendientes(usuarioId).collect { pendientes ->
                    pendientesSincronizacion = pendientes.size.toLong()
                }
            }
            viewModelScope.launch {
                contenedor.metadatos
                    .observar(usuarioId, RecursosSincronizacion.OPERACIONES)
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
        }
    }
}
