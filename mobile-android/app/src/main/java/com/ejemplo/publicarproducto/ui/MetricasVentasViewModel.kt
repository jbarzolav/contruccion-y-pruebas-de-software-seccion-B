package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.MetricasVentasResponse
import com.ejemplo.publicarproducto.network.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * HU 57 - Panel de métricas de ventas del vendedor.
 *
 * La interfaz consume la copia local de Room (Offline First): se guarda
 * únicamente el último resultado válido. Con red, cada refresco pide el
 * panel real al backend; sin red se muestran los últimos datos sincronizados
 * y se informa del estado.
 */
class MetricasVentasViewModel : BaseOfflineViewModel() {

    var metricas by mutableStateOf<MetricasVentasResponse?>(null)
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Preset de periodo elegido ("7", "30", "mes", "mes-anterior", "personalizado"). */
    var preset by mutableStateOf(PRESET_30)
        private set

    /** Compara con el periodo anterior de igual duración. */
    var comparar by mutableStateOf(true)
        private set

    /** Fechas del periodo personalizado (AAAA-MM-DD). */
    var desdePersonalizado by mutableStateOf("")
        private set
    var hastaPersonalizado by mutableStateOf("")
        private set

    /** Error de validación del periodo personalizado. */
    var errorPeriodo by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            contenedor.metricas.observar(usuarioId).collect { panel ->
                metricas = panel
            }
        }
    }

    /** Descarga el panel real del backend y actualiza la copia local. */
    fun cargar() {
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                val periodo = PeriodosMetricas.rango(preset, desdePersonalizado, hastaPersonalizado)
                when (val resultado = contenedor.metricas.refrescar(
                    usuarioId = usuarioId,
                    desde = periodo?.first,
                    hasta = periodo?.second,
                    compararPeriodoAnterior = comparar
                )) {
                    is ResultadoApi.Exito -> sinConexion = false
                    is ResultadoApi.Error -> {
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudieron cargar las métricas de ventas."
            } finally {
                cargando = false
            }
        }
    }

    /** Cambia el preset del periodo y vuelve a consultar. */
    fun cambiarPreset(nuevo: String) {
        preset = nuevo
        errorPeriodo = null
        if (nuevo != PRESET_PERSONALIZADO) cargar()
    }

    /** Activa o desactiva la comparación con el periodo anterior. */
    fun cambiarComparacion(activada: Boolean) {
        comparar = activada
        cargar()
    }

    /** Aplica el rango personalizado indicado por el vendedor. */
    fun aplicarPeriodoPersonalizado(desde: String, hasta: String) {
        desdePersonalizado = desde
        hastaPersonalizado = hasta
        if (desde.isBlank() || hasta.isBlank()) {
            errorPeriodo = "Indica las dos fechas del periodo."
            return
        }
        if (!fechaFiltroValida(desde) || !fechaFiltroValida(hasta)) {
            errorPeriodo = "Usa fechas reales con formato AAAA-MM-DD."
            return
        }
        if (desde > hasta) {
            errorPeriodo = "La fecha inicial no puede superar la fecha final."
            return
        }
        errorPeriodo = null
        preset = PRESET_PERSONALIZADO
        cargar()
    }

    companion object {
        const val PRESET_7 = "7"
        const val PRESET_30 = "30"
        const val PRESET_MES = "mes"
        const val PRESET_MES_ANTERIOR = "mes-anterior"
        const val PRESET_PERSONALIZADO = "personalizado"

        /** Etiquetas de los presets para el selector de periodo. */
        val PERIODOS = listOf(
            PRESET_7 to "Últimos 7 días",
            PRESET_30 to "Últimos 30 días",
            PRESET_MES to "Mes actual",
            PRESET_MES_ANTERIOR to "Mes anterior",
            PRESET_PERSONALIZADO to "Personalizado"
        )
    }
}

/**
 * Rango de fechas de cada preset.
 *
 * El backend usa un criterio inclusivo por día completo en la hora local del
 * servidor: aquí se calculan las mismas fechas (AAAA-MM-DD) para que el
 * periodo pedido sea exactamente el que se analiza.
 */
object PeriodosMetricas {

    /** Par de fechas (desde, hasta) del preset elegido; null si falta una. */
    fun rango(preset: String, desde: String?, hasta: String?): Pair<String, String>? {
        val hoy = LocalDate.now()
        val rango: Pair<LocalDate, LocalDate> = when (preset) {
            MetricasVentasViewModel.PRESET_7 -> hoy.minusDays(6L) to hoy
            MetricasVentasViewModel.PRESET_30 -> hoy.minusDays(29L) to hoy
            MetricasVentasViewModel.PRESET_MES ->
                LocalDate.of(hoy.getYear(), hoy.getMonthValue(), 1) to hoy
            MetricasVentasViewModel.PRESET_MES_ANTERIOR -> {
                val primero = LocalDate.of(hoy.getYear(), hoy.getMonthValue(), 1).minusMonths(1L)
                primero to primero.withDayOfMonth(primero.lengthOfMonth())
            }
            MetricasVentasViewModel.PRESET_PERSONALIZADO -> {
                if (desde.isNullOrBlank() || hasta.isNullOrBlank()) return null
                return desde to hasta
            }
            else -> hoy.minusDays(29L) to hoy
        }
        return rango.first.toString() to rango.second.toString()
    }
}
