package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.DisponibilidadProductoResponse
import com.ejemplo.publicarproducto.model.CalificacionesVendedorResumenResponse
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.ItemCarritoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.model.ResenaProductoPublicaResponse
import com.ejemplo.publicarproducto.network.UserApiClient
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

    var resumenCalificacionesVendedor by mutableStateOf<CalificacionesVendedorResumenResponse?>(null)
        private set
    var cargandoResumenCalificaciones by mutableStateOf(false)
        private set
    var errorResumenCalificaciones by mutableStateOf<String?>(null)
        private set
    var resenasProducto by mutableStateOf<List<ResenaProductoPublicaResponse>>(emptyList())
        private set
    var cargandoResenasProducto by mutableStateOf(false)
        private set
    var errorResenasProducto by mutableStateOf<String?>(null)
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

    // HU 31 - estado y resultado del guardado como favorito.
    var esFavorito by mutableStateOf(false)
        private set
    var cargandoFavorito by mutableStateOf(false)
        private set
    var guardandoFavorito by mutableStateOf(false)
        private set
    var avisoFavorito by mutableStateOf<String?>(null)
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
                val respuesta = UserApiClient.productoApi.agregarAlCarrito(
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
                    "No hay conexión con el backend (10.0.2.2:9000): ${e.message}"
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
        resumenCalificacionesVendedor = null
        cargandoResumenCalificaciones = false
        errorResumenCalificaciones = null
        resenasProducto = emptyList()
        cargandoResenasProducto = false
        errorResenasProducto = null

        // al abrir otro producto se limpia el estado del carrito
        cantidad = 1
        mensajeCarrito = null
        errorCarrito = null

        viewModelScope.launch {
            try {
                val respuestaProducto =
                    UserApiClient.productoApi.obtenerProducto(idProducto)

                val respuestaDisponibilidad =
                    UserApiClient.productoApi.obtenerDisponibilidadProducto(idProducto)

                if (
                    respuestaProducto.isSuccessful &&
                    respuestaProducto.body() != null &&
                    respuestaDisponibilidad.isSuccessful &&
                    respuestaDisponibilidad.body() != null
                ) {
                    producto = respuestaProducto.body()
                    disponibilidad = respuestaDisponibilidad.body()
                    uiState = DetalleProductoUiState.Success
                    producto?.vendedorId?.let(::cargarResumenCalificacionesVendedor)
                    cargarResenasProducto(idProducto)
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
                    "No hay conexión con el backend (10.0.2.2:9000): ${e.message}"
                )
            }
        }
    }

    private fun cargarResumenCalificacionesVendedor(vendedorId: Long) {
        cargandoResumenCalificaciones = true
        errorResumenCalificaciones = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.obtenerResumenCalificacionesVendedor(vendedorId)
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    resumenCalificacionesVendedor = respuesta.body()
                } else {
                    errorResumenCalificaciones = "No se pudieron cargar las calificaciones del vendedor."
                }
            } catch (e: Exception) {
                errorResumenCalificaciones = "No se pudieron cargar las calificaciones del vendedor."
            } finally {
                cargandoResumenCalificaciones = false
            }
        }
    }

    private fun cargarResenasProducto(productoId: Long) {
        cargandoResenasProducto = true
        errorResenasProducto = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.obtenerResenasProducto(productoId)
                if (respuesta.isSuccessful) {
                    resenasProducto = respuesta.body().orEmpty()
                } else {
                    errorResenasProducto = "No se pudieron cargar las reseñas del producto."
                }
            } catch (e: Exception) {
                errorResenasProducto = "No se pudieron cargar las reseñas del producto."
            } finally {
                cargandoResenasProducto = false
            }
        }
    }

    fun cargarEstadoFavorito(idProducto: Long) {
        cargandoFavorito = true
        esFavorito = false
        avisoFavorito = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.consultarEstadoFavorito(idProducto)
                if (respuesta.isSuccessful) {
                    esFavorito = respuesta.body()?.favorito == true
                } else {
                    avisoFavorito = "No se pudo consultar el estado del favorito."
                }
            } catch (e: Exception) {
                avisoFavorito = "No hay conexión para consultar tus favoritos."
            } finally {
                cargandoFavorito = false
            }
        }
    }

    fun agregarFavorito() {
        val id = producto?.id ?: return
        if (guardandoFavorito || esFavorito) return
        guardandoFavorito = true
        avisoFavorito = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.agregarFavorito(id)
                if (respuesta.isSuccessful && respuesta.body()?.favorito == true) {
                    esFavorito = true
                    avisoFavorito = "Producto guardado en tus favoritos."
                } else {
                    avisoFavorito = "Error ${respuesta.code()}: no se pudo guardar el favorito."
                }
            } catch (e: Exception) {
                avisoFavorito = "No hay conexión para guardar el favorito."
            } finally {
                guardandoFavorito = false
            }
        }
    }
}
