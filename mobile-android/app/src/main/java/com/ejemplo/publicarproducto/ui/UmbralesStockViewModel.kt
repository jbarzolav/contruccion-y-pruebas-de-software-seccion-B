package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.AvisoStockBajoResponse
import com.ejemplo.publicarproducto.model.UmbralStockResponse
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.sync.ProgramadorSincronizacion
import kotlinx.coroutines.CancellationException
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
        viewModelScope.launch {
            contenedor.umbrales.observarUmbrales(usuarioId).collect { lista ->
                publicaciones = lista
            }
        }
        viewModelScope.launch {
            contenedor.umbrales.observarAvisos(usuarioId).collect { lista ->
                avisos = lista
            }
        }
        viewModelScope.launch {
            contenedor.umbrales.observarPendientes(usuarioId).collect { mapa ->
                pendientes = mapa
            }
        }
    }

    /** Descarga el panel real del backend y actualiza la copia local. */
    fun cargar() {
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.umbrales.refrescarUmbrales(usuarioId)) {
                    is ResultadoApi.Exito -> sinConexion = false
                    is ResultadoApi.Error -> {
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
                // Los avisos se leen de la bandeja EXISTENTE filtrada por tipo.
                when (val resultado = contenedor.umbrales.refrescarAvisos(usuarioId)) {
                    is ResultadoApi.Exito -> Unit
                    is ResultadoApi.Error -> {
                        if (resultado.temporal) sinConexion = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudieron cargar los umbrales de stock."
            } finally {
                cargando = false
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
        guardandoId = idProducto
        error = null
        mensaje = null
        viewModelScope.launch {
            try {
                contenedor.umbrales.registrarUmbralPendienteLocal(usuarioId, idProducto, umbral)
                contenedor.umbrales.encolarUmbral(usuarioId, idProducto, umbral)
                ProgramadorSincronizacion.programar(contenedor.contexto, usuarioId)
                mensaje = "Umbral guardado en el dispositivo. Pendiente de sincronizar con el servidor."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudo guardar el umbral de stock."
            } finally {
                guardandoId = null
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
