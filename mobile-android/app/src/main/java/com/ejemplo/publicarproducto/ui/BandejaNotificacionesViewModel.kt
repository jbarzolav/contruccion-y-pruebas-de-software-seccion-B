package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.local.TAMANO_PAGINA_BANDEJA
import com.ejemplo.publicarproducto.model.BandejaNotificacionesResponse
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.sync.ProgramadorSincronizacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * HU 53/54 - Estado de la bandeja unificada: filtros combinables, paginación,
 * contador de pendientes y lectura individual o masiva de los avisos propios.
 *
 * Mejora técnica Offline First: la pantalla consume la copia local de Room.
 * Con internet, `cargar` pide la página real al backend y actualiza Room;
 * sin internet se muestran los últimos datos sincronizados y las lecturas se
 * guardan en el dispositivo en una cola persistente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BandejaNotificacionesViewModel : BaseOfflineViewModel() {
    var bandeja by mutableStateOf<BandejaNotificacionesResponse?>(null); private set
    var cargando by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var accionando by mutableStateOf(false); private set

    // Filtros seleccionados; el preset de fecha se convierte en desde/hasta.
    var tipo by mutableStateOf<String?>(null)
    var estado by mutableStateOf<String?>(null)
    var presetFecha by mutableStateOf<String?>("todas")
    var pagina by mutableStateOf(0)

    /** Aviso puntual de la interfaz (guardado local, sincronización, rechazo). */
    var aviso by mutableStateOf<String?>(null); private set

    private data class Filtros(
        val tipo: String?,
        val estado: String?,
        val presetFecha: String?,
        val pagina: Int
    )

    private val filtros = MutableStateFlow(Filtros(null, null, "todas", 0))

    init {
        // La interfaz observa Room: cualquier cambio en la copia local se
        // refleja solo, sin volver a pedir datos al servidor.
        viewModelScope.launch {
            filtros.flatMapLatest { actual ->
                val (desde, hasta) = rangoFechas(actual.presetFecha)
                contenedor.notificaciones.observarBandeja(
                    usuarioId = usuarioId,
                    tipo = actual.tipo,
                    estado = actual.estado,
                    desde = desde,
                    hasta = hasta,
                    pagina = actual.pagina,
                    tamano = TAMANO_PAGINA_BANDEJA
                )
            }.collect { bandeja = it }
        }
    }

    /** Descarga la página real del backend y actualiza la copia local. */
    fun cargar() {
        if (cargando) return
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                val (desde, hasta) = rangoFechas(presetFecha)
                when (val resultado = contenedor.notificaciones.refrescar(
                    usuarioId = usuarioId,
                    tipo = tipo,
                    estado = estado,
                    desde = desde,
                    hasta = hasta,
                    pagina = pagina,
                    tamano = TAMANO_PAGINA_BANDEJA
                )) {
                    is ResultadoApi.Exito -> sinConexion = false
                    is ResultadoApi.Error -> {
                        if (resultado.temporal) {
                            // Sin red: se mantiene la copia local, no es un error.
                            sinConexion = true
                        } else {
                            error = resultado.mensaje
                        }
                    }
                }
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
        filtros.value = Filtros(tipo, estado, presetFecha, pagina)
        cargar()
    }

    fun irAnterior() {
        if (pagina > 0) {
            pagina--
            filtros.value = Filtros(tipo, estado, presetFecha, pagina)
            cargar()
        }
    }

    fun irSiguiente() {
        if (bandeja?.tieneSiguiente == true) {
            pagina++
            filtros.value = Filtros(tipo, estado, presetFecha, pagina)
            cargar()
        }
    }

    /**
     * HU 54 - Marca una notificación propia como leída.
     *
     * La lectura se guarda primero en el dispositivo y se encola para el
     * servidor: si no hay red, se sincronizará al recuperar la conectividad.
     * Hasta que el backend confirme, la interfaz solo informa de que está
     * pendiente de sincronización.
     */
    fun marcarLeida(idNotificacion: Long) {
        if (accionando) return
        accionando = true
        error = null
        viewModelScope.launch {
            try {
                contenedor.notificaciones.registrarLecturaLocal(usuarioId, idNotificacion)
                contenedor.notificaciones.encolarLectura(usuarioId, idNotificacion)
                ProgramadorSincronizacion.programar(contenedor.contexto, usuarioId)
                aviso = "Lectura guardada en el dispositivo. Pendiente de sincronizar con el servidor."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudo marcar la notificación como leída."
            } finally {
                accionando = false
            }
        }
    }

    /**
     * HU 54 - Marca todas las pendientes del usuario como leídas.
     * La lectura masiva se guarda en el dispositivo y se encola.
     */
    fun marcarTodas() {
        if (accionando || (bandeja?.pendientes ?: 0L) <= 0L) return
        accionando = true
        error = null
        viewModelScope.launch {
            try {
                contenedor.notificaciones.registrarLecturaMasivaLocal(usuarioId)
                contenedor.notificaciones.encolarLecturaMasiva(usuarioId)
                ProgramadorSincronizacion.programar(contenedor.contexto, usuarioId)
                aviso = "Lecturas guardadas en el dispositivo. Pendientes de sincronizar con el servidor."
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
    private fun rangoFechas(preset: String?): Pair<String?, String?> {
        if (preset == null || preset == "todas") return null to null
        val hoy = LocalDate.now()
        val dias = when (preset) {
            "7" -> 6L
            "30" -> 29L
            else -> 0L
        }
        return hoy.minusDays(dias).toString() to hoy.toString()
    }
}
