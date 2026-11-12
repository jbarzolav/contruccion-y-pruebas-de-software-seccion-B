package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.AvisoStockBajoResponse
import com.ejemplo.publicarproducto.model.UmbralStockResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * HU 58 - Umbrales de stock y avisos de stock bajo del vendedor.
 *
 * El cambio de umbral se guarda en el dispositivo y se encola en la cola
 * persistente EXISTENTE (no hay una segunda cola): el PUT es idempotente, así
 * que el reintento es seguro. Hasta que el servidor confirme, la interfaz
 * solo informa que el cambio está pendiente de sincronizar.
 */
class UmbralesStockViewModel : BaseOfflineViewModel() {

    var publicaciones by mutableStateOf<List<UmbralStockResponse>>(emptyList())
        private set
    var avisos by mutableStateOf<List<AvisoStockBajoResponse>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var guardandoId by mutableStateOf<Long?>(null)
        private set

    /** Umbral propuesto por el usuario que el servidor todavía no confirmó. */
    var pendientes by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set

    init {
        // R2 - La identidad del usuario se observa de forma reactiva: cuando
        // el usuario cambia se cancelan los collectors del anterior y se
        // limpian sus datos de presentación.
        iniciarObservacionesDeUsuario()
    }

    /**
     * R2 - Observaciones de los datos del usuario activo.
     *
     * Los collectors viven en el alcance del usuario: al cambiar de cuenta se
     * cancelan y se crean de nuevo con el identificador nuevo.
     */
    override fun observarUsuario(usuarioId: Long, alcance: CoroutineScope) {
        alcance.launch {
            contenedor.umbrales.observarUmbrales(usuarioId).collect { lista ->
                publicaciones = lista
            }
        }
        alcance.launch {
            contenedor.umbrales.observarAvisos(usuarioId).collect { lista ->
                avisos = lista
            }
        }
        alcance.launch {
            contenedor.umbrales.observarPendientes(usuarioId).collect { mapa ->
                pendientes = mapa
            }
        }
    }

    /** R2 - No se muestra nada del usuario anterior al cambiar de cuenta. */
    override fun limpiarEstadoDeUsuario() {
        publicaciones = emptyList()
        avisos = emptyList()
        pendientes = emptyMap()
        error = null
        mensaje = null
        cargando = false
        guardandoId = null
    }

    /** Descarga el panel real del backend y actualiza la copia local. */
    fun cargar() {
        // R2 - Se recuerda con qué usuario se lanzó la petición: si mientras
        // tanto cambia la sesión, el resultado NO se muestra en la pantalla
        // del usuario nuevo.
        val usuario = usuarioId
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.umbrales.refrescarUmbrales(usuario)) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) sinConexion = false
                    is ResultadoApi.Error -> {
                        if (usuario != usuarioId) return@launch
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
                // Los avisos se leen de la bandeja EXISTENTE filtrada por tipo.
                when (val resultado = contenedor.umbrales.refrescarAvisos(usuario)) {
                    is ResultadoApi.Exito -> Unit
                    is ResultadoApi.Error -> {
                        if (usuario == usuarioId && resultado.temporal) sinConexion = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) error = e.message ?: "No se pudieron cargar los umbrales de stock."
            } finally {
                if (usuario == usuarioId) cargando = false
            }
        }
    }

    /**
     * Guarda el umbral indicado por el vendedor.
     *
     * El valor se guarda en el dispositivo y se encola: el servidor lo
     * confirmará en la próxima sincronización. Nunca se afirma que el
     * servidor ya aplicó el cambio antes de su confirmación.
     */
    fun guardarUmbral(idProducto: Long, valor: String) {
        if (guardandoId != null) return
        val problema = validarUmbral(valor)
        if (problema != null) {
            error = problema
            return
        }
        val umbral = valor.trim().toIntOrNull() ?: return
        // R2 - La operación se lanza sobre el usuario activo en ese momento.
        val usuario = usuarioId
        guardandoId = idProducto
        error = null
        mensaje = null
        viewModelScope.launch {
            try {
                contenedor.umbrales.registrarUmbralPendienteLocal(usuario, idProducto, umbral)
                contenedor.umbrales.encolarUmbral(usuario, idProducto, umbral)
                contenedor.programacionSincronizacion.programar(usuario)
                if (usuario == usuarioId) {
                    mensaje = "Umbral guardado en el dispositivo. Pendiente de sincronizar con el servidor."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) error = e.message ?: "No se pudo guardar el umbral de stock."
            } finally {
                if (usuario == usuarioId) guardandoId = null
            }
        }
    }

    /** El umbral debe ser un número entero mayor o igual a cero. */
    fun validarUmbral(valor: String): String? {
        val texto = valor.trim()
        if (texto.isEmpty()) return "Indica un umbral (0 o mayor)."
        if (!texto.matches(Regex("^\\d+$"))) return "El umbral debe ser un número entero."
        if (texto.toLongOrNull() == null) return "El umbral es demasiado grande."
        if (texto.toInt() < 0) return "El umbral no puede ser negativo."
        return null
    }
}
