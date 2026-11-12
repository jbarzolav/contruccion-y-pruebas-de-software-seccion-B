package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.launch

sealed interface MisProductosUiState {
    data object Loading : MisProductosUiState
    data object Success : MisProductosUiState
    data class Error(val mensaje: String) : MisProductosUiState
}

/**
 * HU 05 - Publicaciones propias del vendedor.
 * HU 19 - Activar o desactivar el destacado (plan Premium activo).
 *
 * El ViewModel no habla con Retrofit: delega en [ProductosRepositorio].
 */
class MisProductosViewModel : ViewModel() {

    /** Repositorio de productos (se resuelve en cada llamada). */
    private val repositorio: ProductosRepositorio
        get() = ProveedorDatos.actual.productos

    var productos by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set

    var uiState by mutableStateOf<MisProductosUiState>(MisProductosUiState.Loading)
        private set

    fun cargarProductos() {
        uiState = MisProductosUiState.Loading

        viewModelScope.launch {
            when (val resultado = repositorio.listarMisProductos()) {
                is ResultadoApi.Exito -> {
                    productos = resultado.valor
                    uiState = MisProductosUiState.Success
                }

                is ResultadoApi.Error -> {
                    uiState = MisProductosUiState.Error(resultado.mensaje)
                }
            }
        }
    }

    // HU 19 - Activar o desactivar el destacado (plan Premium activo).
    fun gestionarDestacado(idProducto: Long, destacado: Boolean) {
        viewModelScope.launch {
            when (val resultado = repositorio.gestionarDestacado(idProducto, destacado)) {
                is ResultadoApi.Exito -> cargarProductos()

                is ResultadoApi.Error -> {
                    uiState = MisProductosUiState.Error(resultado.mensaje)
                }
            }
        }
    }
}
