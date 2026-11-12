package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PublicarUiState {
    data object Idle : PublicarUiState
    data object Loading : PublicarUiState
    data class Success(val mensaje: String) : PublicarUiState
    data class Error(val mensaje: String) : PublicarUiState
}

/**
 * HU 01 - Publicar producto.
 * HU 02 - Asociar la imagen real del producto (multipart).
 *
 * El ViewModel no habla con Retrofit: delega en [ProductosRepositorio], que
 * arma el `multipart/form-data`. Las reglas del formulario están en
 * [ValidadorPublicacion].
 */
class PublicarProductoViewModel(application: Application) :
    AndroidViewModel(application) {

    /** Repositorio de productos (se resuelve en cada llamada). */
    private val repositorio: ProductosRepositorio
        get() = ProveedorDatos.actual.productos

    var nombre by mutableStateOf("")
    var descripcion by mutableStateOf("")
    var precio by mutableStateOf("")
    var stock by mutableStateOf("")
    var categoria by mutableStateOf("")
    var estado by mutableStateOf("DISPONIBLE")

    /**
     * HU02 - URI de la imagen seleccionada en la galería.
     * Vive en el ViewModel para no perderse al rotar la pantalla.
     */
    var imagenUri by mutableStateOf<String?>(null)
        private set

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

    /**
     * HU02 - Valida el formato y guarda la URI seleccionada.
     */
    fun onImagenChange(uri: Uri?) {
        if (uri == null) {
            quitarImagen()
            return
        }

        if (!formatoPermitido(uri)) {
            imagenUri = null
            erroresCampo = erroresCampo +
                ("imagen" to "Solo se permiten imágenes JPG, JPEG o PNG")
            return
        }

        imagenUri = uri.toString()
        limpiarError("imagen")
    }

    fun quitarImagen() {
        imagenUri = null
        limpiarError("imagen")
    }

    private fun limpiarError(campo: String) {
        if (erroresCampo.containsKey(campo)) {
            erroresCampo = erroresCampo - campo
        }
        uiState = PublicarUiState.Idle
    }

    // ------------------------------------------------------------------
    // Utilidades de imagen
    // ------------------------------------------------------------------

    private fun resolver() = getApplication<Application>().contentResolver

    private fun nombreArchivo(uri: Uri): String? {
        return try {
            resolver().query(uri, null, null, null, null)?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indice >= 0 && cursor.moveToFirst()) cursor.getString(indice) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatoPermitido(uri: Uri): Boolean {
        val mime = resolver().getType(uri)?.lowercase().orEmpty()
        val nombre = nombreArchivo(uri)?.lowercase()

        // Si el proveedor no informa el nombre, se confía en el MIME
        val extensionOk = nombre == null || ValidadorPublicacion.extensionPermitida(nombre)
        val mimeOk = ValidadorPublicacion.mimePermitido(mime)

        return extensionOk && mimeOk
    }

    // ------------------------------------------------------------------
    // Validación
    // ------------------------------------------------------------------

    private fun validar(): Boolean {
        erroresCampo = ValidadorPublicacion.validar(
            nombre = nombre,
            descripcion = descripcion,
            precio = precio,
            stock = stock,
            categoria = categoria,
            requiereImagen = true,
            imagenPresente = !imagenUri.isNullOrBlank()
        )
        return erroresCampo.isEmpty()
    }

    // ------------------------------------------------------------------
    // Publicar (HU01 + HU02)
    // ------------------------------------------------------------------

    fun publicar() {
        if (!validar()) {
            uiState = PublicarUiState.Error(
                "Corrige los campos marcados antes de publicar."
            )
            return
        }

        uiState = PublicarUiState.Loading

        viewModelScope.launch {
            // 1) Crear el producto (el repositorio completa vendedor e imagen)
            val request = ProductoRequest(
                nombre = nombre.trim(),
                descripcion = descripcion.trim(),
                precio = precio.toDouble(),
                stock = stock.toInt(),
                categoria = categoria.trim(),
                estado = estado,
                imagenUrl = "",
                vendedorId = 0L
            )

            val resultado = repositorio.crearProducto(request)

            val idProducto = when (resultado) {
                is ResultadoApi.Exito -> resultado.valor.id

                is ResultadoApi.Error -> {
                    uiState = PublicarUiState.Error(resultado.mensaje)
                    return@launch
                }
            }

            if (idProducto == null) {
                uiState = PublicarUiState.Error(
                    "No se pudo obtener el ID del producto."
                )
                return@launch
            }

            // 2) Subir el archivo REAL de la imagen (multipart)
            val uri = imagenUri?.let { Uri.parse(it) }

            if (uri == null) {
                uiState = PublicarUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:9000): " +
                        "No se pudo leer la imagen seleccionada"
                )
                return@launch
            }

            val mime = resolver().getType(uri).orEmpty()
            val nombre = nombreArchivo(uri)

            val bytes = withContext(Dispatchers.IO) {
                resolver().openInputStream(uri)?.use { entrada -> entrada.readBytes() }
            }

            if (bytes == null) {
                uiState = PublicarUiState.Error(
                    "No hay conexión con el backend (10.0.2.2:9000): " +
                        "No se pudo leer la imagen seleccionada"
                )
                return@launch
            }

            when (val resultadoImagen = repositorio.subirImagen(idProducto, bytes, nombre, mime)) {
                is ResultadoApi.Exito -> {
                    uiState = PublicarUiState.Success(
                        "¡Producto e imagen registrados correctamente! (ID $idProducto)"
                    )
                    limpiarFormulario()
                }

                is ResultadoApi.Error -> {
                    uiState = PublicarUiState.Error(resultadoImagen.mensaje)
                }
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
        imagenUri = null
    }
}
