package com.ejemplo.publicarproducto.ui

import java.math.BigDecimal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.ItemCarritoCantidadRequest
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response

sealed interface CarritoUiState {
    data object Loading : CarritoUiState
    data object Success : CarritoUiState
    data class Error(val mensaje: String) : CarritoUiState
}

/**
 * HU 12 - Carrito de compras: lista de ítems con controles [-] / [+]
 * y subtotales recalculados SIEMPRE por el backend (PUT).
 */
class CarritoViewModel : ViewModel() {

    var items by mutableStateOf<List<ItemCarritoResponse>>(emptyList())
        private set

    var totalCarrito by mutableStateOf(BigDecimal.ZERO)
        private set

    var uiState by mutableStateOf<CarritoUiState>(CarritoUiState.Loading)
        private set

    // ítem que se está actualizando (para deshabilitar sus botones)
    var actualizandoId by mutableStateOf<Long?>(null)
        private set

    var mensajeError by mutableStateOf<String?>(null)
        private set

    fun cargarCarrito() {
        uiState = CarritoUiState.Loading
        mensajeError = null

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.obtenerCarrito()

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    items = respuesta.body()!!
                    cargarTotalCarrito()
                    uiState = CarritoUiState.Success
                } else {
                    uiState = CarritoUiState.Error(
                        "Error ${respuesta.code()}: no se pudo cargar el carrito."
                    )
                }
            } catch (e: Exception) {
                uiState = CarritoUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }

    /**
     * HU 12 - PUT /api/carrito/items/{id} con la nueva cantidad.
     * El backend valida el stock y devuelve el ítem con el subtotal ya recalculado.
     */
    fun cambiarCantidad(item: ItemCarritoResponse, delta: Int) {
        val idItem = item.id ?: return
        val cantidadActual = item.cantidad ?: return
        val nuevaCantidad = cantidadActual + delta

        if (nuevaCantidad < 1) return

        actualizandoId = idItem
        mensajeError = null

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.actualizarCantidadItem(
                    idItemCarrito = idItem,
                    request = ItemCarritoCantidadRequest(cantidad = nuevaCantidad)
                )

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val actualizado = respuesta.body()!!
                    items = items.map { elemento ->
                        if (elemento.id == actualizado.id) actualizado else elemento
                    }
                    cargarTotalCarrito()
                } else {
                    mensajeError = mensajeDeError(respuesta)
                }
            } catch (e: Exception) {
                mensajeError =
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
            } finally {
                actualizandoId = null
            }
        }
    }

    // HU 13 - Eliminar producto del carrito
    fun eliminarProducto(item: ItemCarritoResponse) {
        val idItem = item.id ?: return
        if (actualizandoId != null) return

        actualizandoId = idItem
        mensajeError = null

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.eliminarItemCarrito(idItem)

                if (respuesta.isSuccessful) {
                    items = items.filter { it.id != idItem }
                    cargarTotalCarrito()
                } else {
                    mensajeError = mensajeDeError(respuesta)
                }
            } catch (e: Exception) {
                mensajeError =
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
            } finally {
                actualizandoId = null
            }
        }
    }

    // HU 14 - Consultar el total calculado por Spring Boot
    fun cargarTotalCarrito() {
        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.obtenerTotalCarrito()
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    totalCarrito = respuesta.body()!!.total
                } else {
                    mensajeError = "Error ${respuesta.code()}: no se pudo consultar el total."
                }
            } catch (e: Exception) {
                mensajeError = "No se pudo consultar el total: ${e.message}"
            }
        }
    }

    /** Lee {status, error, messages} del backend para mostrar la regla violada. */
    private fun mensajeDeError(respuesta: Response<*>): String {
        return try {
            val cuerpo = respuesta.errorBody()?.string()
            val error = cuerpo?.let { Gson().fromJson(it, ErrorResponse::class.java) }
            error?.messages?.firstOrNull() ?: "Error ${respuesta.code()}: no se pudo actualizar."
        } catch (e: Exception) {
            "Error ${respuesta.code()}: no se pudo actualizar."
        }
    }
}
