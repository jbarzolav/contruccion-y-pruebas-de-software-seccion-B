package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
import com.ejemplo.publicarproducto.model.PedidoResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response
import java.math.BigDecimal

sealed interface ConfirmarCompraUiState {
    data object Loading : ConfirmarCompraUiState
    data object Success : ConfirmarCompraUiState
    data class Error(val mensaje: String) : ConfirmarCompraUiState
}

/**
 * HU 15 - "Confirmar mi compra": resumen del carrito (lista, subtotales
 * y total) y registro del pedido con POST /api/pedidos.
 *
 * Reglas del backend: carrito vacío → 400, stock insuficiente → 400
 * (no se descuenta nada) y, al tener éxito, se descuenta el stock
 * y se vacía el carrito.
 */
class ConfirmarCompraViewModel : ViewModel() {

    var items by mutableStateOf<List<ItemCarritoResponse>>(emptyList())
        private set

    var totalCarrito by mutableStateOf(BigDecimal.ZERO)
        private set

    var uiState by mutableStateOf<ConfirmarCompraUiState>(ConfirmarCompraUiState.Loading)
        private set

    /** Pedido registrado (comprobante con el ID). */
    var pedido by mutableStateOf<PedidoResponse?>(null)
        private set

    var confirmando by mutableStateOf(false)
        private set

    var mensajeError by mutableStateOf<String?>(null)
        private set

    /** Carga el resumen final: ítems + total calculado por el servidor. */
    fun cargarResumen() {
        uiState = ConfirmarCompraUiState.Loading
        mensajeError = null

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.obtenerCarrito()

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    items = respuesta.body()!!
                    val respuestaTotal = RetrofitClient.productoApi.obtenerTotalCarrito()
                    if (respuestaTotal.isSuccessful && respuestaTotal.body() != null) {
                        totalCarrito = respuestaTotal.body()!!.total
                    }
                    uiState = ConfirmarCompraUiState.Success
                } else {
                    uiState = ConfirmarCompraUiState.Error(
                        "Error ${respuesta.code()}: no se pudo cargar el resumen."
                    )
                }
            } catch (e: Exception) {
                uiState = ConfirmarCompraUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }

    /** HU 15 - POST /api/pedidos: registra el pedido y vacía el carrito. */
    fun confirmarCompra() {
        if (confirmando || items.isEmpty()) return

        confirmando = true
        mensajeError = null

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.confirmarCompra()

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    pedido = respuesta.body()!!
                    items = emptyList()
                    totalCarrito = BigDecimal.ZERO
                } else {
                    mensajeError = mensajeDeError(respuesta)
                }
            } catch (e: Exception) {
                mensajeError =
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
            } finally {
                confirmando = false
            }
        }
    }

    /** Vuelve al modo resumen (por ejemplo, tras ver el comprobante). */
    fun reiniciar() {
        pedido = null
        cargarResumen()
    }

    /** Lee {status, error, messages} del backend para mostrar la regla violada. */
    private fun mensajeDeError(respuesta: Response<*>): String {
        return try {
            val cuerpo = respuesta.errorBody()?.string()
            val error = cuerpo?.let { Gson().fromJson(it, ErrorResponse::class.java) }
            error?.messages?.firstOrNull() ?: "Error ${respuesta.code()}: no se pudo registrar el pedido."
        } catch (e: Exception) {
            "Error ${respuesta.code()}: no se pudo registrar el pedido."
        }
    }
}
