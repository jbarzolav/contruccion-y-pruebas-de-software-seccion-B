package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.repository.FavoritosRepositorio
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
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

/**
 * HU 06 / HU 09 - Búsqueda por nombre con criterio de orden.
 * HU 07 / HU 08 - Catálogo público.
 * HU 20 - Recomendaciones de la última búsqueda.
 * HU 31 / HU 33 - Favoritos del cliente.
 *
 * El ViewModel no habla con Retrofit: delega en [ProductosRepositorio] y en
 * [FavoritosRepositorio].
 */
class BuscarProductosViewModel : ViewModel() {

    /** Repositorio de productos (se resuelve en cada llamada). */
    private val repositorio: ProductosRepositorio
        get() = ProveedorDatos.actual.productos

    /** Repositorio de favoritos (se resuelve en cada llamada). */
    private val repositorioFavoritos: FavoritosRepositorio
        get() = ProveedorDatos.actual.favoritos

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

    /**
     * HU 20 - recomendaciones que la pantalla debe mostrar: descarta las que
     * ya aparecen en los resultados de la búsqueda.
     */
    val recomendacionesVisibles: List<ProductoResponse>
        get() {
            val idsResultados = productos.mapNotNull { it.id }.toSet()
            return recomendaciones.filter { it.id != null && it.id !in idsResultados }
        }

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
            when (
                val resultado = repositorio.buscar(
                    nombre = criterio,
                    sort = criterioOrden.ifBlank { null }
                )
            ) {
                is ResultadoApi.Exito -> {
                    productos = resultado.valor
                    uiState = BuscarProductosUiState.Success
                }

                is ResultadoApi.Error -> {
                    productos = emptyList()
                    uiState = BuscarProductosUiState.Error(resultado.mensaje)
                }
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
                when (val resultado = repositorio.listarRecomendaciones(criterio)) {
                    is ResultadoApi.Exito -> {
                        recomendaciones = resultado.valor
                        uiStateRecomendaciones = RecomendacionesUiState.Success
                    }

                    is ResultadoApi.Error -> {
                        recomendaciones = emptyList()
                        uiStateRecomendaciones = RecomendacionesUiState.Error(resultado.mensaje)
                    }
                }
            } catch (e: CancellationException) {
                // Cancelada por un término más nuevo: no tocar los estados
                throw e
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
            when (val resultado = repositorio.listarCatalogo()) {
                is ResultadoApi.Exito -> {
                    productos = resultado.valor
                    uiState = BuscarProductosUiState.Success
                }

                is ResultadoApi.Error -> {
                    productos = emptyList()
                    uiState = BuscarProductosUiState.Error(resultado.mensaje)
                }
            }
        }
    }

    fun cargarFavoritos() {
        if (cargandoFavoritos) return
        cargandoFavoritos = true
        errorFavoritos = null
        viewModelScope.launch {
            try {
                when (val resultado = repositorioFavoritos.listarFavoritos()) {
                    is ResultadoApi.Exito -> favoritos = resultado.valor

                    is ResultadoApi.Error -> errorFavoritos = resultado.mensaje
                }
            } catch (e: CancellationException) {
                throw e
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
                when (val resultado = repositorioFavoritos.agregarFavorito(id)) {
                    is ResultadoApi.Exito -> {
                        favoritos = listOf(producto) + favoritos.filterNot { it.id == id }
                        avisoFavorito = "${producto.nombre.orEmpty()} se guardó en tus favoritos."
                    }

                    is ResultadoApi.Error -> avisoFavorito = resultado.mensaje
                }
            } catch (e: CancellationException) {
                throw e
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
                when (val resultado = repositorioFavoritos.quitarFavorito(id)) {
                    is ResultadoApi.Exito -> {
                        favoritos = favoritos.filterNot { it.id == id }
                        avisoFavorito = "${producto.nombre.orEmpty()} se quitó de tus favoritos."
                    }

                    is ResultadoApi.Error -> {
                        if (resultado.codigo == 404) {
                            favoritos = favoritos.filterNot { it.id == id }
                            avisoFavorito = "El favorito ya no existía; la lista fue actualizada."
                        } else {
                            avisoFavorito = resultado.mensaje
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                idQuitandoFavorito = null
            }
        }
    }
}
