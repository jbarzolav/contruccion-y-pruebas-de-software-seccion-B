package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.DisponibilidadProductoResponse
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.ItemCarritoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response

sealed interface DetalleProductoUiState {
    data object Loading : DetalleProductoUiState
    data object Success : DetalleProductoUiState
    data class Error(val mensaje: String) : DetalleProductoUiState
}

/**
 * HU 07 / HU 10 - Detalle y disponibilidad de un producto.
 * HU 11 - Cantidad a agregar y alta de ítems en el carrito.
 */
class DetalleProductoViewModel : ViewModel() {

    var producto by mutableStateOf<ProductoResponse?>(null)
        private set

    var disponibilidad by mutableStateOf<DisponibilidadProductoResponse?>(null)
        private set

    var uiState by mutableStateOf<DetalleProductoUiState>(DetalleProductoUiState.Loading)
        private set

    // HU 11 - selector de cantidad del detalle
    var cantidad by mutableStateOf(1)
        private set

    var agregandoCarrito by mutableStateOf(false)
        private set

    // estado visual tras enviar la petición del carrito
    var mensajeCarrito by mutableStateOf<String?>(null)
        private set

    var errorCarrito by mutableStateOf<String?>(null)
        private set

    fun cambiarCantidad(delta: Int) {
        val stockMaximo = disponibilidad?.stock ?: Int.MAX_VALUE
        cantidad = (cantidad + delta).coerceIn(1, stockMaximo)
    }

    /**
     * HU 11 - POST /api/carrito/items y refresco del estado visual del carrito.
     */
    fun agregarAlCarrito() {
        val productoActual = producto ?: return

        agregandoCarrito = true
        mensajeCarrito = null
        errorCarrito = null

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.agregarAlCarrito(
                    ItemCarritoRequest(
                        productoId = productoActual.id ?: 0L,
                        cantidad = cantidad
                    )
                )

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val item = respuesta.body()!!
                    mensajeCarrito =
                        "✓ Se agregó ${item.cantidad} unidad(es) al carrito " +
                                "(subtotal S/ ${item.subtotal})"
                } else {
                    errorCarrito = mensajeDeError(respuesta)
                }
            } catch (e: Exception) {
                errorCarrito =
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
            } finally {
                agregandoCarrito = false
            }
        }
    }

    /** Lee {status, error, messages} del backend para mostrar la regla violada. */
    private fun mensajeDeError(respuesta: Response<*>): String {
        return try {
            val cuerpo = respuesta.errorBody()?.string()
            val error = cuerpo?.let { Gson().fromJson(it, ErrorResponse::class.java) }
            error?.messages?.firstOrNull() ?: "Error ${respuesta.code()}: no se pudo agregar."
        } catch (e: Exception) {
            "Error ${respuesta.code()}: no se pudo agregar."
        }
    }

    fun cargarProducto(idProducto: Long) {
        uiState = DetalleProductoUiState.Loading

        // al abrir otro producto se limpia el estado del carrito
        cantidad = 1
        mensajeCarrito = null
        errorCarrito = null

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
