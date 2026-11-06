package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.data.Mapeadores
import com.ejemplo.publicarproducto.local.Ambitos
import com.ejemplo.publicarproducto.model.NotificacionResponse
import com.ejemplo.publicarproducto.model.PedidoVendedorResponse
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.sync.ProgramadorSincronizacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * HU 48 - Pedidos recibidos con solo los productos del vendedor.
 * HU 49 - Cambio de estado del pedido (el backend valida la transición).
 *
 * Mejora técnica Offline First: la lista se observa desde la copia local de
 * Room. El cambio de estado se guarda en el dispositivo y se encola en la
 * cola persistente: si no hay red se sincroniza al recuperar la conexión y,
 * si el servidor lo rechaza, se informa del rechazo sin affirmar que la
 * operación se confirmó.
 */
class PedidosRecibidosViewModel : BaseOfflineViewModel() {

    var pedidos by mutableStateOf<List<PedidoVendedorResponse>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var guardandoId by mutableStateOf<Long?>(null)
        private set
    var notificaciones by mutableStateOf<List<NotificacionResponse>>(emptyList())
        private set
    var pedidoEnfocadoId by mutableStateOf<Long?>(null)
        private set

    /** Estado propuesto por el usuario que el servidor todavía no validó. */
    var estadosPendientes by mutableStateOf<Map<Long, String>>(emptyMap())
        private set

    init {
        viewModelScope.launch {
            contenedor.pedidos.observarVentas(usuarioId).collect { ventas ->
                pedidos = ventas.pedidos
                estadosPendientes = ventas.estadosPendientes
            }
        }
        viewModelScope.launch {
            contenedor.bd.notificacionDao().observarTodas(usuarioId).collect { avisos ->
                notificaciones = avisos.map { Mapeadores.aDto(it) }
            }
        }
    }

    /** Descarga los pedidos recibidos reales del backend y actualiza Room. */
    fun cargar() {
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.refrescarVentas(usuarioId)) {
                    is ResultadoApi.Exito -> sinConexion = false
                    is ResultadoApi.Error -> {
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudieron cargar los pedidos recibidos."
            } finally {
                cargando = false
            }
        }
    }

    /** Los avisos ya se observan desde la copia local de Room. */
    fun cargarNotificaciones() {
        // Sin llamada de red: la información local ya está actualizada.
    }

    fun enfocarPedido(idPedido: Long) {
        pedidoEnfocadoId = idPedido
    }

    /**
     * HU 49 - El vendedor propone el siguiente estado; el backend lo valida.
     *
     * La propuesta se guarda en el dispositivo y se encola. Hasta que el
     * servidor la confirme, la interfaz solo indica que está pendiente.
     */
    fun actualizarEstado(idPedido: Long, estado: String) {
        if (guardandoId != null || estado.isBlank()) return
        guardandoId = idPedido
        error = null
        mensaje = null
        viewModelScope.launch {
            try {
                contenedor.pedidos.registrarEstadoPendienteLocal(
                    usuarioId = usuarioId,
                    idPedido = idPedido,
                    ambito = Ambitos.VENTAS,
                    estado = estado
                )
                contenedor.pedidos.encolarEstadoPedido(
                    usuarioId = usuarioId,
                    idPedido = idPedido,
                    ambito = Ambitos.VENTAS,
                    estado = estado
                )
                ProgramadorSincronizacion.programar(contenedor.contexto, usuarioId)
                mensaje = "N.º de compra: $idPedido · Estado $estado guardado en el dispositivo. " +
                    "Pendiente de sincronizar con el servidor."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudo actualizar el estado del pedido."
            } finally {
                guardandoId = null
            }
        }
    }
}
