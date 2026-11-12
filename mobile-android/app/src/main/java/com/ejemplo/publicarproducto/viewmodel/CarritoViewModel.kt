package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import java.math.BigDecimal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.repository.CarritoRepositorio
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.launch

sealed interface CarritoUiState {
    data object Loading : CarritoUiState
    data object Success : CarritoUiState
    data class Error(val mensaje: String) : CarritoUiState
}

/**
 * HU 12 - Carrito de compras: lista de ítems con controles [-] / [+]
 * y subtotales recalculados SIEMPRE por el backend (PUT).
 * HU 13 - Eliminar producto del carrito.
 * HU 14 - Total del carrito.
 *
 * El ViewModel no habla con Retrofit: delega en [CarritoRepositorio].
 */
class CarritoViewModel : ViewModel() {

    /** Repositorio del carrito (se resuelve en cada llamada). */
    private val repositorio: CarritoRepositorio
        get() = ProveedorDatos.actual.carrito

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
            when (val resultado = repositorio.obtenerCarrito()) {
                is ResultadoApi.Exito -> {
                    items = resultado.valor
                    cargarTotalCarrito()
                    uiState = CarritoUiState.Success
                }

                is ResultadoApi.Error -> {
                    uiState = CarritoUiState.Error(resultado.mensaje)
                }
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
        val nuevaCantidad = ReglasCarrito.nuevaCantidad(cantidadActual, delta) ?: return

        actualizandoId = idItem
        mensajeError = null

        viewModelScope.launch {
            when (val resultado = repositorio.actualizarCantidad(idItem, nuevaCantidad)) {
                is ResultadoApi.Exito -> {
                    val actualizado = resultado.valor
                    items = items.map { elemento ->
                        if (elemento.id == actualizado.id) actualizado else elemento
                    }
                    cargarTotalCarrito()
                }

                is ResultadoApi.Error -> mensajeError = resultado.mensaje
            }
            actualizandoId = null
        }
    }

    // HU 13 - Eliminar producto del carrito
    fun eliminarProducto(item: ItemCarritoResponse) {
        val idItem = item.id ?: return
        if (actualizandoId != null) return

        actualizandoId = idItem
        mensajeError = null

        viewModelScope.launch {
            when (val resultado = repositorio.eliminarItem(idItem)) {
                is ResultadoApi.Exito -> {
                    items = items.filter { it.id != idItem }
                    cargarTotalCarrito()
                }

                is ResultadoApi.Error -> mensajeError = resultado.mensaje
            }
            actualizandoId = null
        }
    }

    // HU 14 - Consultar el total calculado por Spring Boot
    fun cargarTotalCarrito() {
        viewModelScope.launch {
            when (val resultado = repositorio.obtenerTotal()) {
                is ResultadoApi.Exito -> totalCarrito = resultado.valor.total

                is ResultadoApi.Error -> mensajeError = resultado.mensaje
            }
        }
    }
}
