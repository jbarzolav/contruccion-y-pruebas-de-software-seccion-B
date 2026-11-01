package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.PedidoResponse
import com.ejemplo.publicarproducto.model.ResenaEdicionRequest
import com.ejemplo.publicarproducto.network.UserApiClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response

class MisPedidosViewModel : ViewModel() {
    var pedidos by mutableStateOf<List<PedidoResponse>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var guardando by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set

    fun cargar() {
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.listarMisPedidos()
                if (respuesta.isSuccessful) pedidos = respuesta.body().orEmpty()
                else error = mensajeDeError(respuesta)
            } catch (e: Exception) {
                error = e.message ?: "No se pudieron cargar tus pedidos."
            } finally { cargando = false }
        }
    }

    fun editarResena(idPedido: Long, idResena: Long, texto: String) {
        val normalizado = texto.trim()
        if (guardando || normalizado.isEmpty() || normalizado.length > 1000) return
        guardando = true
        mensaje = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.editarResena(idResena, ResenaEdicionRequest(normalizado))
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val actualizada = respuesta.body()!!
                    pedidos = pedidos.map { pedido ->
                        if (pedido.idPedido != idPedido) pedido
                        else pedido.copy(resenas = pedido.resenas.orEmpty().map { if (it.id == idResena) actualizada else it })
                    }
                    mensaje = "Reseña actualizada correctamente."
                } else mensaje = mensajeDeError(respuesta)
            } catch (e: Exception) { mensaje = e.message ?: "No se pudo actualizar la reseña." }
            finally { guardando = false }
        }
    }

    private fun mensajeDeError(respuesta: Response<*>): String = try {
        val cuerpo = respuesta.errorBody()?.string()
        Gson().fromJson(cuerpo, ErrorResponse::class.java)?.messages?.firstOrNull()
            ?: "Error ${respuesta.code()} al consultar pedidos."
    } catch (_: Exception) { "Error ${respuesta.code()} al consultar pedidos." }
}
