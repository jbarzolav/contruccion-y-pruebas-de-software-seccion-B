package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.repository.CarritoRepositorio
import com.ejemplo.publicarproducto.model.repository.FavoritosRepositorio
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import com.ejemplo.publicarproducto.model.CalificacionesVendedorResumenResponse
import com.ejemplo.publicarproducto.model.DisponibilidadProductoResponse
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.model.ResenaProductoPublicaResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.launch

sealed interface DetalleProductoUiState {
    data object Loading : DetalleProductoUiState
    data object Success : DetalleProductoUiState
    data class Error(val mensaje: String) : DetalleProductoUiState
}

/**
 * HU 07 / HU 10 - Detalle y disponibilidad de un producto.
 * HU 11 - Cantidad a agregar y alta de ítems en el carrito.
 *
 * El ViewModel no habla con Retrofit: delega en [ProductosRepositorio],
 * [FavoritosRepositorio] y [CarritoRepositorio].
 */
class DetalleProductoViewModel : ViewModel() {

    /** Repositorio de productos (se resuelve en cada llamada). */
    private val repositorio: ProductosRepositorio
        get() = ProveedorDatos.actual.productos

    /** Repositorio de favoritos (se resuelve en cada llamada). */
    private val repositorioFavoritos: FavoritosRepositorio
        get() = ProveedorDatos.actual.favoritos

    /** Repositorio del carrito (se resuelve en cada llamada). */
    private val repositorioCarrito: CarritoRepositorio
        get() = ProveedorDatos.actual.carrito

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
     *
     * El carrito vive en [CarritoRepositorio]: con esto el ViewModel ya no
     * hace ninguna llamada directa a Retrofit.
     */
    fun agregarAlCarrito() {
        val productoActual = producto ?: return

        agregandoCarrito = true
        mensajeCarrito = null
        errorCarrito = null

        viewModelScope.launch {
            when (
                val resultado = repositorioCarrito.agregarItem(
                    idProducto = productoActual.id ?: 0L,
                    cantidad = cantidad
                )
            ) {
                is ResultadoApi.Exito -> {
                    val item = resultado.valor
                    mensajeCarrito =
                        "✓ Se agregó ${item.cantidad} unidad(es) al carrito " +
                                "(subtotal S/ ${item.subtotal})"
                }

                is ResultadoApi.Error -> errorCarrito = resultado.mensaje
            }
            agregandoCarrito = false
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
            val resultadoProducto = repositorio.obtenerDetalleProducto(idProducto)

            // Fallo de red: antes no se intentaba la segunda llamada.
            if (resultadoProducto is ResultadoApi.Error && resultadoProducto.codigo == null) {
                producto = null
                disponibilidad = null
                uiState = DetalleProductoUiState.Error(resultadoProducto.mensaje)
                return@launch
            }

            val resultadoDisponibilidad = repositorio.obtenerDisponibilidad(idProducto)

            when {
                resultadoProducto is ResultadoApi.Exito &&
                    resultadoDisponibilidad is ResultadoApi.Exito -> {
                    producto = resultadoProducto.valor
                    disponibilidad = resultadoDisponibilidad.valor
                    uiState = DetalleProductoUiState.Success
                    producto?.vendedorId?.let(::cargarResumenCalificacionesVendedor)
                    cargarResenasProducto(idProducto)
                }

                else -> {
                    producto = null
                    disponibilidad = null

                    val codigo = when {
                        resultadoProducto is ResultadoApi.Error -> resultadoProducto.codigo
                        resultadoDisponibilidad is ResultadoApi.Error -> resultadoDisponibilidad.codigo
                        else -> null
                    }

                    uiState = DetalleProductoUiState.Error(
                        when {
                            resultadoProducto is ResultadoApi.Error &&
                                resultadoProducto.codigo == null ->
                                resultadoProducto.mensaje

                            resultadoDisponibilidad is ResultadoApi.Error &&
                                resultadoDisponibilidad.codigo == null ->
                                resultadoDisponibilidad.mensaje

                            codigo == 404 -> "Producto no encontrado (id $idProducto)."

                            else -> "Error $codigo: no se pudo cargar el detalle."
                        }
                    )
                }
            }
        }
    }

    private fun cargarResumenCalificacionesVendedor(vendedorId: Long) {
        cargandoResumenCalificaciones = true
        errorResumenCalificaciones = null
        viewModelScope.launch {
            when (val resultado = repositorio.resumenCalificacionesVendedor(vendedorId)) {
                is ResultadoApi.Exito ->
                    resumenCalificacionesVendedor = resultado.valor

                is ResultadoApi.Error ->
                    errorResumenCalificaciones = resultado.mensaje
            }
            cargandoResumenCalificaciones = false
        }
    }

    private fun cargarResenasProducto(productoId: Long) {
        cargandoResenasProducto = true
        errorResenasProducto = null
        viewModelScope.launch {
            when (val resultado = repositorio.listarResenasProducto(productoId)) {
                is ResultadoApi.Exito ->
                    resenasProducto = resultado.valor

                is ResultadoApi.Error ->
                    errorResenasProducto = resultado.mensaje
            }
            cargandoResenasProducto = false
        }
    }

    fun cargarEstadoFavorito(idProducto: Long) {
        cargandoFavorito = true
        esFavorito = false
        avisoFavorito = null
        viewModelScope.launch {
            when (val resultado = repositorioFavoritos.consultarEstadoFavorito(idProducto)) {
                is ResultadoApi.Exito -> esFavorito = resultado.valor.favorito == true

                is ResultadoApi.Error -> avisoFavorito = resultado.mensaje
            }
            cargandoFavorito = false
        }
    }

    fun agregarFavorito() {
        val id = producto?.id ?: return
        if (guardandoFavorito || esFavorito) return
        guardandoFavorito = true
        avisoFavorito = null
        viewModelScope.launch {
            when (val resultado = repositorioFavoritos.agregarFavorito(id)) {
                is ResultadoApi.Exito -> {
                    esFavorito = true
                    avisoFavorito = "Producto guardado en tus favoritos."
                }

                is ResultadoApi.Error -> avisoFavorito = resultado.mensaje
            }
            guardandoFavorito = false
        }
    }
}
