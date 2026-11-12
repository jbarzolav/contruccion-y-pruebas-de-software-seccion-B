package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.local.TAMANO_PAGINA_BANDEJA
import com.ejemplo.publicarproducto.model.BandejaNotificacionesResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
        // R2 - La identidad del usuario se observa de forma reactiva: cuando
        // el usuario cambia se cancelan los collectors del anterior y se
        // limpian sus datos de presentación.
        iniciarObservacionesDeUsuario()
    }

    /**
     * R2 - Observaciones de los datos del usuario activo.
     *
     * El collector vive en el alcance del usuario: al cambiar de cuenta se
     * cancela y se crea de nuevo con el identificador nuevo, de modo que la
     * bandeja observada siempre pertenece al usuario activo.
     */
    override fun observarUsuario(usuarioId: Long, alcance: CoroutineScope) {
        // La interfaz observa Room: cualquier cambio en la copia local se
        // refleja solo, sin volver a pedir datos al servidor.
        alcance.launch {
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

    /** R2 - No se muestra nada del usuario anterior al cambiar de cuenta. */
    override fun limpiarEstadoDeUsuario() {
        bandeja = null
        error = null
        aviso = null
        cargando = false
        accionando = false
        tipo = null
        estado = null
        presetFecha = "todas"
        pagina = 0
        filtros.value = Filtros(null, null, "todas", 0)
    }

    /** Descarga la página real del backend y actualiza la copia local. */
    fun cargar() {
        if (cargando) return
        // R2 - Se recuerda con qué usuario se lanzó la petición: si mientras
        // tanto cambia la sesión, el resultado NO se muestra en la pantalla
        // del usuario nuevo.
        val usuario = usuarioId
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                val (desde, hasta) = rangoFechas(presetFecha)
                when (val resultado = contenedor.notificaciones.refrescar(
                    usuarioId = usuario,
                    tipo = tipo,
                    estado = estado,
                    desde = desde,
                    hasta = hasta,
                    pagina = pagina,
                    tamano = TAMANO_PAGINA_BANDEJA
                )) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) sinConexion = false
                    is ResultadoApi.Error -> {
                        if (usuario != usuarioId) return@launch
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
                if (usuario == usuarioId) error = e.message ?: "No se pudieron cargar las notificaciones."
            } finally {
                if (usuario == usuarioId) cargando = false
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
        // R2 - La operación se lanza sobre el usuario activo en ese momento.
        val usuario = usuarioId
        accionando = true
        error = null
        viewModelScope.launch {
            try {
                contenedor.notificaciones.registrarLecturaLocal(usuario, idNotificacion)
                contenedor.notificaciones.encolarLectura(usuario, idNotificacion)
                contenedor.programacionSincronizacion.programar(usuario)
                if (usuario == usuarioId) {
                    aviso = "Lectura guardada en el dispositivo. Pendiente de sincronizar con el servidor."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) error = e.message ?: "No se pudo marcar la notificación como leída."
            } finally {
                if (usuario == usuarioId) accionando = false
            }
        }
    }

    /**
     * HU 54 - Marca todas las pendientes del usuario como leídas.
     * La lectura masiva se guarda en el dispositivo y se encola.
     */
    fun marcarTodas() {
        if (accionando || (bandeja?.pendientes ?: 0L) <= 0L) return
        // R2 - La operación se lanza sobre el usuario activo en ese momento.
        val usuario = usuarioId
        accionando = true
        error = null
        viewModelScope.launch {
            try {
                contenedor.notificaciones.registrarLecturaMasivaLocal(usuario)
                contenedor.notificaciones.encolarLecturaMasiva(usuario)
                contenedor.programacionSincronizacion.programar(usuario)
                if (usuario == usuarioId) {
                    aviso = "Lecturas guardadas en el dispositivo. Pendientes de sincronizar con el servidor."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) error = e.message ?: "No se pudieron marcar las notificaciones como leídas."
            } finally {
                if (usuario == usuarioId) accionando = false
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
