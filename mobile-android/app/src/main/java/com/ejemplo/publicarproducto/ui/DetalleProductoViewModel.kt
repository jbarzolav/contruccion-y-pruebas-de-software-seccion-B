package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import kotlinx.coroutines.launch

sealed interface DetalleProductoUiState {
    data object Loading : DetalleProductoUiState
    data object Success : DetalleProductoUiState
    data class Error(val mensaje: String) : DetalleProductoUiState
}

/**
 * HU 07 - Visualizar el detalle de un producto.
 * Consume GET /api/productos/{idProducto} (404 si no existe).
 */
class DetalleProductoViewModel : ViewModel() {

    var producto by mutableStateOf<ProductoResponse?>(null)
        private set

    var uiState by mutableStateOf<DetalleProductoUiState>(DetalleProductoUiState.Loading)
        private set

    fun cargarProducto(idProducto: Long) {
        uiState = DetalleProductoUiState.Loading

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.obtenerProducto(idProducto)

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    producto = respuesta.body()
                    uiState = DetalleProductoUiState.Success
                } else {
                    producto = null
                    uiState = DetalleProductoUiState.Error(
                        when (respuesta.code()) {
                            404 -> "Producto no encontrado (id $idProducto)."
                            else -> "Error ${respuesta.code()}: no se pudo cargar el detalle."
                        }
                    )
                }
            } catch (e: Exception) {
                producto = null
                uiState = DetalleProductoUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }
}
