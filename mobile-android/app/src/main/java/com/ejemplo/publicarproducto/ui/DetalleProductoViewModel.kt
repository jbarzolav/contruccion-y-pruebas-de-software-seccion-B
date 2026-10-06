package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.DisponibilidadProductoResponse
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import kotlinx.coroutines.launch

sealed interface DetalleProductoUiState {
    data object Loading : DetalleProductoUiState
    data object Success : DetalleProductoUiState
    data class Error(val mensaje: String) : DetalleProductoUiState
}

/**
 * HU 07 / HU 10 - Detalle y disponibilidad de un producto.
 */
class DetalleProductoViewModel : ViewModel() {

    var producto by mutableStateOf<ProductoResponse?>(null)
        private set

    var disponibilidad by mutableStateOf<DisponibilidadProductoResponse?>(null)
        private set

    var uiState by mutableStateOf<DetalleProductoUiState>(DetalleProductoUiState.Loading)
        private set

    fun cargarProducto(idProducto: Long) {
        uiState = DetalleProductoUiState.Loading

        viewModelScope.launch {
            try {
                val respuestaProducto =
                    RetrofitClient.productoApi.obtenerProducto(idProducto)

                val respuestaDisponibilidad =
                    RetrofitClient.productoApi.obtenerDisponibilidadProducto(idProducto)

                if (
                    respuestaProducto.isSuccessful &&
                    respuestaProducto.body() != null &&
                    respuestaDisponibilidad.isSuccessful &&
                    respuestaDisponibilidad.body() != null
                ) {
                    producto = respuestaProducto.body()
                    disponibilidad = respuestaDisponibilidad.body()
                    uiState = DetalleProductoUiState.Success
                } else {
                    producto = null
                    disponibilidad = null

                    val codigo = if (!respuestaProducto.isSuccessful) {
                        respuestaProducto.code()
                    } else {
                        respuestaDisponibilidad.code()
                    }

                    uiState = DetalleProductoUiState.Error(
                        when (codigo) {
                            404 -> "Producto no encontrado (id $idProducto)."
                            else -> "Error $codigo: no se pudo cargar el detalle."
                        }
                    )
                }
            } catch (e: Exception) {
                producto = null
                disponibilidad = null

                uiState = DetalleProductoUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }
}
