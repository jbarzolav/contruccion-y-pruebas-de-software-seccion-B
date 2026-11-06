package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.PedidoDetalleResponse
import com.ejemplo.publicarproducto.network.ResultadoApi
import kotlinx.coroutines.CancellationException
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

    private val idPedidoActual = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            idPedidoActual.flatMapLatest { idPedido ->
                if (idPedido <= 0L) flowOf(null)
                else contenedor.pedidos.observarDetalle(usuarioId, idPedido)
            }.collect { pedido = it }
        }
    }

    fun cargar(idPedido: Long) {
        if (idPedido <= 0) {
            error = "El pedido solicitado no es válido."
            return
        }
        idPedidoActual.value = idPedido
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.refrescarDetalle(usuarioId, idPedido)) {
                    is ResultadoApi.Exito -> sinConexion = false
                    is ResultadoApi.Error -> {
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudo cargar el detalle del pedido."
            } finally {
                cargando = false
            }
        }
    }
}
