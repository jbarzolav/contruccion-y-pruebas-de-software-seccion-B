package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.PedidoDetalleResponse
import com.ejemplo.publicarproducto.network.UserApiClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response

/** HU 50 - Carga el detalle del pedido propio (productos, precios y total). */
class DetallePedidoViewModel : ViewModel() {
    var pedido by mutableStateOf<PedidoDetalleResponse?>(null)
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun cargar(idPedido: Long) {
        if (idPedido <= 0) {
            error = "El pedido solicitado no es válido."
            return
        }
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.obtenerDetallePedido(idPedido)
                if (respuesta.isSuccessful) pedido = respuesta.body()
                else error = mensajeDeError(respuesta)
            } catch (e: Exception) {
                error = e.message ?: "No se pudo cargar el detalle del pedido."
            } finally { cargando = false }
        }
    }

    private fun mensajeDeError(respuesta: Response<*>): String = try {
        val cuerpo = respuesta.errorBody()?.string()
        Gson().fromJson(cuerpo, ErrorResponse::class.java)?.messages?.firstOrNull()
            ?: "Error ${respuesta.code()} al consultar el detalle del pedido."
    } catch (_: Exception) { "Error ${respuesta.code()} al consultar el detalle del pedido." }
}
