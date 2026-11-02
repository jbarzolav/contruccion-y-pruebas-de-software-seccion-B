package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
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

    private fun mensajeDeError(respuesta: Response<*>): String = try {
        val cuerpo = respuesta.errorBody()?.string()
        Gson().fromJson(cuerpo, ErrorResponse::class.java)?.messages?.firstOrNull()
            ?: "Error ${respuesta.code()} al consultar pedidos recibidos."
    } catch (_: Exception) { "Error ${respuesta.code()} al consultar pedidos recibidos." }
}
