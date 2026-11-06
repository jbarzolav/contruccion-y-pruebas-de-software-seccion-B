package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.BandejaNotificacionesResponse
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.network.UserApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response
import java.time.LocalDate

/**
 * HU 53/54 - Estado de la bandeja unificada: filtros combinables, paginación,
 * contador de pendientes y lectura individual o masiva de los avisos propios.
 */
class BandejaNotificacionesViewModel : ViewModel() {
    var bandeja by mutableStateOf<BandejaNotificacionesResponse?>(null); private set
    var cargando by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var accionando by mutableStateOf(false); private set

    // Filtros seleccionados; el preset de fecha se convierte en desde/hasta.
    var tipo by mutableStateOf<String?>(null)
    var estado by mutableStateOf<String?>(null)
    var presetFecha by mutableStateOf<String?>("todas")
    var pagina by mutableStateOf(0)

    private val api get() = UserApiClient.productoApi

    fun cargar() {
        if (cargando) return
        cargando = true
        error = null
        val (desde, hasta) = rangoFechas()
        viewModelScope.launch {
            try {
                bandeja = exigir(api.listarBandeja(tipo, estado, desde, hasta, pagina))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudieron cargar las notificaciones."
            } finally {
                cargando = false
            }
        }
    }

    /** HU 53 - Cambiar cualquier filtro vuelve a la primera página. */
    fun filtrar(nuevoTipo: String?, nuevoEstado: String?, nuevoPreset: String?) {
        tipo = nuevoTipo
        estado = nuevoEstado
        presetFecha = nuevoPreset
        pagina = 0
        cargar()
    }

    fun irAnterior() {
        if (pagina > 0) {
            pagina--
            cargar()
        }
    }

    fun irSiguiente() {
        if (bandeja?.tieneSiguiente == true) {
            pagina++
            cargar()
        }
    }

    /** HU 54 - Marca una notificación propia como leída y recarga la página. */
    fun marcarLeida(idNotificacion: Long) {
        if (accionando) return
        accionando = true
        error = null
        viewModelScope.launch {
            try {
                exigir(api.marcarNotificacionLeida(idNotificacion))
                cargar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudo marcar la notificación como leída."
            } finally {
                accionando = false
            }
        }
    }

    /** HU 54 - Marca todas las pendientes del usuario como leídas. */
    fun marcarTodas() {
        if (accionando || (bandeja?.pendientes ?: 0L) <= 0L) return
        accionando = true
        error = null
        viewModelScope.launch {
            try {
                exigir(api.marcarTodasComoLeidas())
                cargar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudieron marcar las notificaciones como leídas."
            } finally {
                accionando = false
            }
        }
    }

    /** Convierte el preset (hoy / 7 / 30 / todas) en un rango desde/hasta. */
    private fun rangoFechas(): Pair<String?, String?> {
        if (presetFecha == null || presetFecha == "todas") return null to null
        val hoy = LocalDate.now()
        val dias = when (presetFecha) {
            "7" -> 6L
            "30" -> 29L
            else -> 0L
        }
        return hoy.minusDays(dias).toString() to hoy.toString()
    }

    private fun <T> exigir(respuesta: Response<T>): T {
        if (respuesta.isSuccessful) return requireNotNull(respuesta.body())
        val mensaje = runCatching {
            Gson().fromJson(respuesta.errorBody()?.string(), ErrorResponse::class.java)
                ?.messages?.firstOrNull()
        }.getOrNull()
        throw IllegalStateException(mensaje ?: "Error ${respuesta.code()}. Intenta nuevamente.")
    }
}
