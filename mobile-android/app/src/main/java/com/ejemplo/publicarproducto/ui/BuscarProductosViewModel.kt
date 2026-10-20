package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.network.UserApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface BuscarProductosUiState {
    data object Initial : BuscarProductosUiState
    data object Loading : BuscarProductosUiState
    data object Success : BuscarProductosUiState
    data class Error(val mensaje: String) : BuscarProductosUiState
}

/**
 * HU 20 - estados de la sección de recomendaciones.
 * Mismo patrón que BuscarProductosUiState: Success con la lista
 * vacía equivale al estado Empty ("no hay recomendaciones").
 */
sealed interface RecomendacionesUiState {
    data object Initial : RecomendacionesUiState
    data object Loading : RecomendacionesUiState
    data object Success : RecomendacionesUiState
    data class Error(val mensaje: String) : RecomendacionesUiState
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

    /** HU 20 - recomendaciones relacionadas con la última búsqueda. */
    var uiStateRecomendaciones by mutableStateOf<RecomendacionesUiState>(
        RecomendacionesUiState.Initial
    )
        private set

    var recomendaciones by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set

    // HU 31 - favoritos persistidos y estado visible en catálogo/lista.
    var favoritos by mutableStateOf<List<ProductoResponse>>(emptyList())
        private set
    var cargandoFavoritos by mutableStateOf(false)
        private set
    var errorFavoritos by mutableStateOf<String?>(null)
        private set
    var idGuardandoFavorito by mutableStateOf<Long?>(null)
        private set
    var avisoFavorito by mutableStateOf<String?>(null)
        private set
    var idQuitandoFavorito by mutableStateOf<Long?>(null)
        private set

    /** Término realmente consultado (títula la sección en la UI). */
    var terminoRecomendaciones by mutableStateOf("")
        private set

    /** HU 20 - evita que una respuesta vieja pise la de un término nuevo. */
    private var jobRecomendaciones: Job? = null

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

        // HU 20 - recomendaciones en paralelo con la búsqueda (sin await)
        buscarRecomendaciones(criterio)

        viewModelScope.launch {
            try {
                val respuesta =
                    UserApiClient.productoApi.buscarProductosPorNombre(
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
                    "No hay conexión con el backend (10.0.2.2:9000): ${e.message}"
                )
            }
        }
    }

    /**
     * HU 20 - GET /api/recomendaciones?termino={texto}.
     *
     * El Job anterior se cancela antes de lanzar el nuevo: si el usuario
     * cambia rápido de término, la respuesta vieja jamás pisa los
     * estados del término actual (se relanza la CancellationException).
     * Mismo criterio que el frontend web: término en blanco -> no se
     * consulta y la sección queda oculta.
     */
    private fun buscarRecomendaciones(criterio: String) {
        jobRecomendaciones?.cancel()

        terminoRecomendaciones = criterio
        recomendaciones = emptyList()
        uiStateRecomendaciones = RecomendacionesUiState.Loading

        jobRecomendaciones = viewModelScope.launch {
            try {
                val respuesta =
                    UserApiClient.productoApi.obtenerRecomendaciones(criterio)

                if (respuesta.isSuccessful) {
                    recomendaciones = respuesta.body().orEmpty()
                    uiStateRecomendaciones = RecomendacionesUiState.Success
                } else {
                    recomendaciones = emptyList()
                    uiStateRecomendaciones = RecomendacionesUiState.Error(
                        "Error ${respuesta.code()}: no se pudieron cargar las recomendaciones."
                    )
                }
            } catch (e: CancellationException) {
                // Cancelada por un término más nuevo: no tocar los estados
                throw e
            } catch (e: Exception) {
                recomendaciones = emptyList()
                uiStateRecomendaciones = RecomendacionesUiState.Error(
                    "No se pudieron cargar las recomendaciones (10.0.2.2:9000): ${e.message}"
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
                val respuesta = UserApiClient.productoApi.listarCatalogo()

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
                    "No hay conexión con el backend (10.0.2.2:9000): ${e.message}"
                )
            }
        }
    }

    fun cargarFavoritos() {
        if (cargandoFavoritos) return
        cargandoFavoritos = true
        errorFavoritos = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.listarFavoritos()
                if (respuesta.isSuccessful) {
                    favoritos = respuesta.body().orEmpty()
                } else {
                    errorFavoritos = "Error ${respuesta.code()}: no se pudieron cargar tus favoritos."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFavoritos = "No hay conexión para cargar tus favoritos."
            } finally {
                cargandoFavoritos = false
            }
        }
    }

    fun agregarFavorito(producto: ProductoResponse) {
        val id = producto.id ?: return
        if (idGuardandoFavorito != null || favoritos.any { it.id == id }) return
        idGuardandoFavorito = id
        avisoFavorito = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.agregarFavorito(id)
                if (respuesta.isSuccessful && respuesta.body()?.favorito == true) {
                    favoritos = listOf(producto) + favoritos.filterNot { it.id == id }
                    avisoFavorito = "${producto.nombre.orEmpty()} se guardó en tus favoritos."
                } else {
                    avisoFavorito = "Error ${respuesta.code()}: no se pudo guardar el favorito."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                avisoFavorito = "No hay conexión para guardar el favorito."
            } finally {
                idGuardandoFavorito = null
            }
        }
    }

    /** HU33 - quita el favorito y conserva el producto en el catálogo. */
    fun quitarFavorito(producto: ProductoResponse) {
        val id = producto.id ?: return
        if (idQuitandoFavorito != null) return
        idQuitandoFavorito = id
        avisoFavorito = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.productoApi.quitarFavorito(id)
                if (respuesta.isSuccessful) {
                    favoritos = favoritos.filterNot { it.id == id }
                    avisoFavorito = "${producto.nombre.orEmpty()} se quitó de tus favoritos."
                } else if (respuesta.code() == 404) {
                    favoritos = favoritos.filterNot { it.id == id }
                    avisoFavorito = "El favorito ya no existía; la lista fue actualizada."
                } else {
                    avisoFavorito = "Error ${respuesta.code()}: no se pudo quitar el favorito."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                avisoFavorito = "No hay conexión para quitar el favorito."
            } finally {
                idQuitandoFavorito = null
            }
        }
    }
}
