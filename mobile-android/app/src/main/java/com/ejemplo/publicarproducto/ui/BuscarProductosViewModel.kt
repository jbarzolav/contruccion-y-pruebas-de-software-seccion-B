package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import kotlinx.coroutines.launch

sealed interface BuscarProductosUiState {
    data object Initial : BuscarProductosUiState
    data object Loading : BuscarProductosUiState
    data object Success : BuscarProductosUiState
    data class Error(val mensaje: String) : BuscarProductosUiState
}

class BuscarProductosViewModel : ViewModel() {

    var nombre by mutableStateOf("")
        private set

    var criterioOrden by mutableStateOf("")
        private set

    var productos by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set

    var uiState by mutableStateOf<BuscarProductosUiState>(BuscarProductosUiState.Initial)
        private set

    fun cambiarNombre(valor: String) {
        nombre = valor
    }

    fun cambiarOrden(valor: String) {
        criterioOrden = valor

        if (nombre.trim().isNotBlank()) {
            buscarProductos()
        }
    }

    fun buscarProductos() {
        val criterio = nombre.trim()

        if (criterio.isBlank()) {
            productos = emptyList()
            uiState = BuscarProductosUiState.Error(
                "Ingresa un nombre de producto para buscar."
            )
            return
        }

        uiState = BuscarProductosUiState.Loading

        viewModelScope.launch {
            try {
                val respuesta =
                    RetrofitClient.productoApi.buscarProductosPorNombre(
                        nombre = criterio,
                        sort = criterioOrden.ifBlank { null }
                    )

                if (respuesta.isSuccessful) {
                    productos = respuesta.body().orEmpty()
                    uiState = BuscarProductosUiState.Success
                } else {
                    productos = emptyList()
                    uiState = BuscarProductosUiState.Error(
                        "Error ${respuesta.code()}: no se pudo realizar la búsqueda."
                    )
                }
            } catch (e: Exception) {
                productos = emptyList()
                uiState = BuscarProductosUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }

    /**
     * HU 07/HU 08 - Catálogo público (solo DISPONIBLE).
     * Se carga al abrir la pestaña para que las tarjetas sean
     * seleccionables y lleven al detalle.
     */
    fun cargarCatalogo() {
        uiState = BuscarProductosUiState.Loading

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.listarCatalogo()

                if (respuesta.isSuccessful) {
                    productos = respuesta.body().orEmpty()
                    uiState = BuscarProductosUiState.Success
                } else {
                    productos = emptyList()
                    uiState = BuscarProductosUiState.Error(
                        "Error ${respuesta.code()}: no se pudo cargar el catálogo."
                    )
                }
            } catch (e: Exception) {
                productos = emptyList()
                uiState = BuscarProductosUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }
}