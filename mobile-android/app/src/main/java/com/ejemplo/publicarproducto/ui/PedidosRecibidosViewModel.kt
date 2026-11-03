package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.EstadoPedidoRequest
import com.ejemplo.publicarproducto.model.PedidoVendedorResponse
import com.ejemplo.publicarproducto.network.UserApiClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response

class PedidosRecibidosViewModel : ViewModel() {
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

    fun cargar() {
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.listarPedidosRecibidos()
                if (respuesta.isSuccessful) pedidos = respuesta.body().orEmpty()
                else error = mensajeDeError(respuesta)
            } catch (e: Exception) {
                error = e.message ?: "No se pudieron cargar los pedidos recibidos."
            } finally { cargando = false }
        }
    }

    /** HU 49 - El vendedor propone el siguiente estado; el backend lo valida. */
    fun actualizarEstado(idPedido: Long, estado: String) {
        if (guardandoId != null || estado.isBlank()) return
        guardandoId = idPedido
        error = null
        mensaje = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.actualizarEstadoPedido(idPedido, EstadoPedidoRequest(estado))
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val actualizada = respuesta.body()!!
                    pedidos = pedidos.map { pedido ->
                        if (pedido.idPedido != idPedido) pedido
                        else pedido.copy(
                            estado = actualizada.estado ?: pedido.estado,
                            fechaActualizacion = actualizada.fechaActualizacion ?: pedido.fechaActualizacion,
                            estadosPermitidos = actualizada.estadosPermitidos ?: pedido.estadosPermitidos
                        )
                    }
                    mensaje = "Pedido #$idPedido: estado actualizado a ${actualizada.estado}."
                } else error = mensajeDeError(respuesta)
            } catch (e: Exception) {
                error = e.message ?: "No se pudo actualizar el estado del pedido."
            } finally { guardandoId = null }
        }
    }

    private fun mensajeDeError(respuesta: Response<*>): String = try {
        val cuerpo = respuesta.errorBody()?.string()
        Gson().fromJson(cuerpo, ErrorResponse::class.java)?.messages?.firstOrNull()
            ?: "Error ${respuesta.code()} al consultar pedidos recibidos."
    } catch (_: Exception) { "Error ${respuesta.code()} al consultar pedidos recibidos." }
}
