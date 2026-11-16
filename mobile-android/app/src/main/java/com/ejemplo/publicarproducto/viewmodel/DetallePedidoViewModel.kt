package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.PedidoDetalleResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * HU 50 - Carga el detalle del pedido propio (productos, precios y total).
 *
 * Mejora técnica Offline First: el detalle se observa desde la copia local de
 * Room, de modo que se puede consultar sin conexión; con internet se
 * descarga el detalle real del backend y la copia local se actualiza.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetallePedidoViewModel : BaseOfflineViewModel() {

    var pedido by mutableStateOf<PedidoDetalleResponse?>(null)
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** HU 59.7 - Hay una cancelación en vuelo (bloquea dobles envíos). */
    var cancelando by mutableStateOf(false)
        private set

    /** HU 59.7 - Resultado del último intento de cancelación (éxito o error). */
    var mensajeCancelacion by mutableStateOf<String?>(null)
        private set

    /** HU 59.7 - El último intento de cancelación falló (pinta el mensaje en rojo). */
    var errorCancelacion by mutableStateOf(false)
        private set

    private val idPedidoActual = MutableStateFlow(0L)

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
     * cancela y se crea de nuevo con el identificador nuevo, de modo que el
     * detalle observado siempre pertenece al usuario activo.
     */
    override fun observarUsuario(usuarioId: Long, alcance: CoroutineScope) {
        alcance.launch {
            idPedidoActual.flatMapLatest { idPedido ->
                if (idPedido <= 0L) flowOf(null)
                else contenedor.pedidos.observarDetalle(usuarioId, idPedido)
            }.collect { pedido = it }
        }
    }

    /** R2 - No se muestra nada del usuario anterior al cambiar de cuenta. */
    override fun limpiarEstadoDeUsuario() {
        pedido = null
        error = null
        cargando = false
        cancelando = false
        mensajeCancelacion = null
        errorCancelacion = false
        idPedidoActual.value = 0L
    }

    fun cargar(idPedido: Long) {
        if (idPedido <= 0) {
            error = "El pedido solicitado no es válido."
            return
        }
        // R2 - Se recuerda con qué usuario se lanzó la petición: si mientras
        // tanto cambia la sesión, el resultado NO se muestra en la pantalla
        // del usuario nuevo.
        val usuario = usuarioId
        idPedidoActual.value = idPedido
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.refrescarDetalle(usuario, idPedido)) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) sinConexion = false
                    is ResultadoApi.Error -> {
                        if (usuario != usuarioId) return@launch
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) error = e.message ?: "No se pudo cargar el detalle del pedido."
            } finally {
                if (usuario == usuarioId) cargando = false
            }
        }
    }

    /**
     * HU 59.7 - Cancela la compra desde el detalle mientras el backend la
     * permita (solo estado REGISTRADO).
     *
     * [cancelando] es la bandera anti-doble envío: mientras haya un POST en
     * vuelo no se envía otro. El estado nuevo de la respuesta se aplica a la
     * copia local (Room) y la tarjeta se actualiza sola a "Cancelado". El
     * resultado (éxito o el mensaje de error del backend) queda visible en
     * [mensajeCancelacion].
     */
    fun cancelarPedido() {
        val idPedido = idPedidoActual.value
        if (cancelando || idPedido <= 0) return
        // R2 - Se recuerda con qué usuario se lanzó la petición.
        val usuario = usuarioId
        cancelando = true
        mensajeCancelacion = null
        errorCancelacion = false
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.cancelarPedido(usuario, idPedido)) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) {
                        sinConexion = false
                        mensajeCancelacion = "Compra cancelada."
                    }
                    is ResultadoApi.Error -> {
                        if (usuario != usuarioId) return@launch
                        if (resultado.temporal) sinConexion = true
                        mensajeCancelacion = resultado.mensaje
                        errorCancelacion = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) {
                    mensajeCancelacion = e.message ?: "No se pudo cancelar la compra."
                    errorCancelacion = true
                }
            } finally {
                if (usuario == usuarioId) cancelando = false
            }
        }
    }
}
