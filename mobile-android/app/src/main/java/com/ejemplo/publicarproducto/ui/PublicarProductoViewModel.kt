package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.ImagenProductoRequest
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.launch

sealed interface PublicarUiState {
    data object Idle : PublicarUiState
    data object Loading : PublicarUiState
    data class Success(val mensaje: String) : PublicarUiState
    data class Error(val mensaje: String) : PublicarUiState
}

class PublicarProductoViewModel : ViewModel() {

    var nombre by mutableStateOf("")
    var descripcion by mutableStateOf("")
    var precio by mutableStateOf("")
    var stock by mutableStateOf("")
    var categoria by mutableStateOf("")
    var estado by mutableStateOf("DISPONIBLE")
    var imagenUrl by mutableStateOf("")

    var erroresCampo by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var uiState by mutableStateOf<PublicarUiState>(PublicarUiState.Idle)
        private set

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

    fun onImagenChange(v: String) {
        imagenUrl = v
        limpiarError("imagen")
    }

    fun quitarImagen() {
        imagenUrl = ""
    }

    private fun limpiarError(campo: String) {
        if (erroresCampo.containsKey(campo)) {
            erroresCampo = erroresCampo - campo
        }
        uiState = PublicarUiState.Idle
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

        if (imagenUrl.isBlank()) {
            errores["imagen"] = "La imagen es obligatoria"
        }

        erroresCampo = errores
        return erroresCampo.isEmpty()
    }

    fun publicar() {
        if (!validar()) {
            uiState = PublicarUiState.Error(
                "Corrige los campos marcados antes de publicar."
            )
            return
        }

        val urlImagenBackend = "https://ejemplo.com/producto.jpg"

        val request = ProductoRequest(
            nombre = nombre.trim(),
            descripcion = descripcion.trim(),
            precio = precio.toDouble(),
            stock = stock.toInt(),
            categoria = categoria.trim(),
            estado = estado,
            imagenUrl = urlImagenBackend,
            vendedorId = 1L
        )

        uiState = PublicarUiState.Loading

        viewModelScope.launch {
            try {
                val respuesta =
                    RetrofitClient.productoApi.crearProducto(request)

                if (respuesta.isSuccessful) {

                    val idProducto = respuesta.body()?.id

                    if (idProducto != null) {

                        val imagenRequest = ImagenProductoRequest(
                            imagenUrl = urlImagenBackend,
                            vendedorId = 1L
                        )

                        val respuestaImagen =
                            RetrofitClient.productoApi.agregarImagen(
                                idProducto,
                                imagenRequest
                            )

                        if (respuestaImagen.isSuccessful) {
                            uiState = PublicarUiState.Success(
                                "¡Producto e imagen registrados correctamente! (ID $idProducto)"
                            )
                            limpiarFormulario()
                        } else {
                            uiState = PublicarUiState.Error(
                                "El producto fue creado, pero no se pudo registrar la imagen."
                            )
                        }

                    } else {
                        uiState = PublicarUiState.Error(
                            "No se pudo obtener el ID del producto."
                        )
                    }

                } else {
                    uiState = PublicarUiState.Error(
                        mensajeDeError(
                            respuesta.code(),
                            respuesta.errorBody()?.string()
                        )
                    )
                }

            } catch (e: Exception) {
                uiState = PublicarUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"
                )
            }
        }
    }

    fun cancelar() {
        limpiarFormulario()
        erroresCampo = emptyMap()
        uiState = PublicarUiState.Idle
    }

    private fun limpiarFormulario() {
        nombre = ""
        descripcion = ""
        precio = ""
        stock = ""
        categoria = ""
        estado = "DISPONIBLE"
        imagenUrl = ""
    }

    private fun mensajeDeError(codigo: Int, cuerpo: String?): String {
        return try {
            val error = Gson().fromJson(
                cuerpo,
                ErrorResponse::class.java
            )

            val mensajes = error?.messages?.joinToString(" | ")

            when {
                !mensajes.isNullOrBlank() -> mensajes
                else -> "Error $codigo: no se pudo registrar el producto."
            }

        } catch (_: Exception) {
            "Error $codigo: no se pudo registrar el producto."
        }
    }
}