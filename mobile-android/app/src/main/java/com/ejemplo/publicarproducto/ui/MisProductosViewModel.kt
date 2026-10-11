package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.DestacarRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import kotlinx.coroutines.launch

sealed interface MisProductosUiState {
    data object Loading : MisProductosUiState
    data object Success : MisProductosUiState
    data class Error(val mensaje: String) : MisProductosUiState
}

class MisProductosViewModel : ViewModel() {

    companion object {
        private const val VENDEDOR_ID = 1L
    }

    var productos by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set

    var uiState by mutableStateOf<MisProductosUiState>(MisProductosUiState.Loading)
        private set

    fun cargarProductos() {
        uiState = MisProductosUiState.Loading

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.obtenerMisProductos(VENDEDOR_ID)

                if (respuesta.isSuccessful) {
                    productos = respuesta.body().orEmpty()
                    uiState = MisProductosUiState.Success
                } else {
                    uiState = MisProductosUiState.Error(
                        "Error ${respuesta.code()}: no se pudieron cargar los productos."
                    )
                }
            } catch (e: Exception) {
                uiState = MisProductosUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }

    // HU 19 - Activar o desactivar el destacado (plan Premium activo).
    fun gestionarDestacado(idProducto: Long, destacado: Boolean) {
        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi
                    .gestionarDestacado(idProducto, DestacarRequest(destacado), VENDEDOR_ID)

                if (respuesta.isSuccessful) {
                    cargarProductos()
                } else {
                    uiState = MisProductosUiState.Error(
                        "Error ${respuesta.code()}: no se pudo actualizar el destacado."
                    )
                }
            } catch (e: Exception) {
                uiState = MisProductosUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }
}