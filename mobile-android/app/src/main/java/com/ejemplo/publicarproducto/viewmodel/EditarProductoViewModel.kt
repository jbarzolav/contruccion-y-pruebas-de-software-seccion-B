package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import com.ejemplo.publicarproducto.model.EstadoProductoRequest
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.launch

sealed interface EditarUiState {
    data object Idle : EditarUiState
    data object Loading : EditarUiState
    data class Success(val mensaje: String) : EditarUiState
    data class Error(val mensaje: String) : EditarUiState
}

/**
 * HU 03 - Editar información del producto
 * HU 04 - Retirar publicación (baja lógica)
 *
 * El ViewModel no habla con Retrofit: delega en [ProductosRepositorio].
 * Las reglas del formulario están en [ValidadorPublicacion].
 */
class EditarProductoViewModel : ViewModel() {

    companion object {
        private const val ESTADO_RETIRADO = "RETIRADO"
    }

    /** Repositorio de productos (se resuelve en cada llamada). */
    private val repositorio: ProductosRepositorio
        get() = ProveedorDatos.actual.productos

    var idTexto by mutableStateOf("")
    var nombre by mutableStateOf("")
    var descripcion by mutableStateOf("")
    var precio by mutableStateOf("")
    var stock by mutableStateOf("")
    var categoria by mutableStateOf("")
    var estado by mutableStateOf("DISPONIBLE")

    /** Producto cargado desde el backend (precarga del formulario). */
    var producto by mutableStateOf<ProductoResponse?>(null)
        private set

    var erroresCampo by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var uiState by mutableStateOf<EditarUiState>(EditarUiState.Idle)
        private set

    /** Controla el diálogo de confirmación antes de retirar (HU 04). */
    var mostrarDialogoRetiro by mutableStateOf(false)
        private set

    val estaRetirado: Boolean
        get() = producto?.estado.equals(ESTADO_RETIRADO, ignoreCase = true)

    // ------------------------------------------------------------------
    // Cambios de campo
    // ------------------------------------------------------------------

    fun onIdChange(v: String) {
        idTexto = v
        limpiarError("id")
    }

    fun onNombreChange(v: String) {
        nombre = v
        limpiarError("nombre")
    }

    fun onDescripcionChange(v: String) {
        descripcion = v
        limpiarError("descripcion")
    }

    fun onPrecioChange(v: String) {
        precio = v
        limpiarError("precio")
    }

    fun onStockChange(v: String) {
        stock = v
        limpiarError("stock")
    }

    fun onCategoriaChange(v: String) {
        categoria = v
        limpiarError("categoria")
    }

    fun onEstadoChange(v: String) {
        estado = v
    }

    private fun limpiarError(campo: String) {
        if (erroresCampo.containsKey(campo)) {
            erroresCampo = erroresCampo - campo
        }
        uiState = EditarUiState.Idle
    }

    // ------------------------------------------------------------------
    // HU 03 - Cargar producto (GET) y precargar el formulario
    // ------------------------------------------------------------------

    fun cargarProducto() {
        val id = idTexto.trim().toLongOrNull()

        if (id == null) {
            erroresCampo = erroresCampo + ("id" to "El ID debe ser un número")
            return
        }

        uiState = EditarUiState.Loading

        viewModelScope.launch {
            when (val resultado = repositorio.obtenerProducto(id)) {
                is ResultadoApi.Exito -> {
                    precargar(resultado.valor)
                    uiState = EditarUiState.Success("Producto $id cargado correctamente.")
                }

                is ResultadoApi.Error -> {
                    uiState = EditarUiState.Error(resultado.mensaje)
                }
            }
        }
    }

    private fun precargar(p: ProductoResponse) {
        producto = p
        idTexto = p.id?.toString() ?: idTexto
        nombre = p.nombre.orEmpty()
        descripcion = p.descripcion.orEmpty()
        precio = p.precio?.toPlainString().orEmpty()
        stock = p.stock?.toString().orEmpty()
        categoria = p.categoria.orEmpty()
        estado = p.estado ?: "DISPONIBLE"
        erroresCampo = emptyMap()
    }

    // ------------------------------------------------------------------
    // HU 03 - Guardar cambios (PUT)
    // ------------------------------------------------------------------

    fun guardar() {
        val productoActual = producto
        if (productoActual == null) {
            uiState = EditarUiState.Error("Primero carga un producto con su ID.")
            return
        }

        if (!validar()) {
            uiState = EditarUiState.Error("Corrige los campos marcados antes de guardar.")
            return
        }

        val request = ProductoRequest(
            nombre = nombre.trim(),
            descripcion = descripcion.trim(),
            precio = precio.toDouble(),
            stock = stock.toInt(),
            categoria = categoria.trim(),
            estado = estado,
            imagenUrl = productoActual.imagenUrl.orEmpty(),
            // El repositorio completa el vendedor con el dueño de la
            // publicación o, si no se conoce, con el de la sesión.
            vendedorId = 0L
        )

        uiState = EditarUiState.Loading

        viewModelScope.launch {
            when (
                val resultado = repositorio.actualizarProducto(
                    idProducto = productoActual.id ?: return@launch,
                    request = request,
                    vendedorIdPropietario = productoActual.vendedorId
                )
            ) {
                is ResultadoApi.Exito -> {
                    precargar(resultado.valor)
                    uiState = EditarUiState.Success(
                        "¡Producto actualizado correctamente! (ID ${productoActual.id})"
                    )
                }

                is ResultadoApi.Error -> {
                    uiState = EditarUiState.Error(resultado.mensaje)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // HU 04 - Retirar publicación (baja lógica)
    // ------------------------------------------------------------------

    fun pedirConfirmacionRetiro() {
        if (producto == null) {
            uiState = EditarUiState.Error("Primero carga un producto con su ID.")
            return
        }
        if (estaRetirado) return

        mostrarDialogoRetiro = true
    }

    fun cancelarRetiro() {
        mostrarDialogoRetiro = false
    }

    fun retirar() {
        mostrarDialogoRetiro = false

        val productoActual = producto ?: return

        uiState = EditarUiState.Loading

        viewModelScope.launch {
            when (
                val resultado = repositorio.cambiarEstado(
                    idProducto = productoActual.id ?: return@launch,
                    request = EstadoProductoRequest(ESTADO_RETIRADO),
                    vendedorIdPropietario = productoActual.vendedorId
                )
            ) {
                is ResultadoApi.Exito -> {
                    val actualizado = resultado.valor
                    producto = actualizado
                    estado = actualizado.estado ?: ESTADO_RETIRADO

                    uiState = EditarUiState.Success(
                        "Publicación retirada (estado $estado). " +
                            "No se eliminó: es una baja lógica."
                    )
                }

                is ResultadoApi.Error -> {
                    uiState = EditarUiState.Error(resultado.mensaje)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    fun limpiar() {
        producto = null
        idTexto = ""
        nombre = ""
        descripcion = ""
        precio = ""
        stock = ""
        categoria = ""
        estado = "DISPONIBLE"
        erroresCampo = emptyMap()
        mostrarDialogoRetiro = false
        uiState = EditarUiState.Idle
    }

    private fun validar(): Boolean {
        erroresCampo = ValidadorPublicacion.validar(
            nombre = nombre,
            descripcion = descripcion,
            precio = precio,
            stock = stock,
            categoria = categoria,
            requiereImagen = false
        )
        return erroresCampo.isEmpty()
    }
}
