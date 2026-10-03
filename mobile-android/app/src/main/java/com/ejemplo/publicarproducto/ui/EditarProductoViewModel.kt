package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.EstadoProductoRequest
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import com.google.gson.Gson
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
 */
class EditarProductoViewModel : ViewModel() {

    companion object {
        private const val VENDEDOR_ID = 1L
        private const val ESTADO_RETIRADO = "RETIRADO"
    }

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
            try {
                val respuesta = RetrofitClient.productoApi.obtenerProducto(id)

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    precargar(respuesta.body()!!)
                    uiState = EditarUiState.Success("Producto $id cargado correctamente.")
                } else {
                    uiState = EditarUiState.Error(
                        mensajeDeError(
                            respuesta.code(),
                            respuesta.errorBody()?.string(),
                            "cargar el producto"
                        )
                    )
                }
            } catch (e: Exception) {
                uiState = EditarUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
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
            vendedorId = productoActual.vendedorId ?: VENDEDOR_ID
        )

        uiState = EditarUiState.Loading

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.actualizarProducto(
                    productoActual.id ?: return@launch,
                    request,
                    productoActual.vendedorId ?: VENDEDOR_ID
                )

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    precargar(respuesta.body()!!)
                    uiState = EditarUiState.Success(
                        "¡Producto actualizado correctamente! (ID ${productoActual.id})"
                    )
                } else {
                    uiState = EditarUiState.Error(
                        mensajeDeError(
                            respuesta.code(),
                            respuesta.errorBody()?.string(),
                            "actualizar el producto"
                        )
                    )
                }
            } catch (e: Exception) {
                uiState = EditarUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
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
            try {
                val respuesta = RetrofitClient.productoApi.cambiarEstado(
                    productoActual.id ?: return@launch,
                    EstadoProductoRequest(ESTADO_RETIRADO),
                    productoActual.vendedorId ?: VENDEDOR_ID
                )

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val actualizado = respuesta.body()!!
                    producto = actualizado
                    estado = actualizado.estado ?: ESTADO_RETIRADO

                    uiState = EditarUiState.Success(
                        "Publicación retirada (estado $estado). " +
                            "No se eliminó: es una baja lógica."
                    )
                } else {
                    uiState = EditarUiState.Error(
                        mensajeDeError(
                            respuesta.code(),
                            respuesta.errorBody()?.string(),
                            "retirar la publicación"
                        )
                    )
                }
            } catch (e: Exception) {
                uiState = EditarUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
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
        val errores = mutableMapOf<String, String>()

        if (nombre.isBlank()) {
            errores["nombre"] = "El nombre es obligatorio"
        }

        if (descripcion.isBlank()) {
            errores["descripcion"] = "La descripción es obligatoria"
        }

        val precioValor = precio.toDoubleOrNull()

        when {
            precio.isBlank() ->
                errores["precio"] = "El precio es obligatorio"

            precioValor == null ->
                errores["precio"] = "El precio no es un número válido"

            precioValor <= 0 ->
                errores["precio"] = "El precio debe ser mayor a 0"
        }

        val stockValor = stock.toIntOrNull()

        if (stock.isBlank()) {
            errores["stock"] = "El stock es obligatorio"
        } else if (stockValor == null) {
            errores["stock"] = "El stock no es un número válido"
        } else if (stockValor < 0) {
            errores["stock"] = "El stock no puede ser negativo"
        }

        if (categoria.isBlank()) {
            errores["categoria"] = "La categoría es obligatoria"
        }

        erroresCampo = errores
        return erroresCampo.isEmpty()
    }

    private fun mensajeDeError(codigo: Int, cuerpo: String?, accion: String): String {
        return try {
            val error = Gson().fromJson(cuerpo, ErrorResponse::class.java)
            val mensajes = error?.messages?.joinToString(" | ")

            when {
                !mensajes.isNullOrBlank() -> mensajes

                codigo == 404 -> "El producto no existe (HTTP 404)."

                codigo == 403 -> "No tienes permiso sobre este producto (HTTP 403)."

                else -> "Error $codigo: no se pudo $accion."
            }
        } catch (_: Exception) {
            "Error $codigo: no se pudo $accion."
        }
    }
}
