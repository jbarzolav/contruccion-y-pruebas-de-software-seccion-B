package com.ejemplo.publicarproducto.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException

sealed interface PublicarUiState {
    data object Idle : PublicarUiState
    data object Loading : PublicarUiState
    data class Success(val mensaje: String) : PublicarUiState
    data class Error(val mensaje: String) : PublicarUiState
}

class PublicarProductoViewModel(application: Application) :
    AndroidViewModel(application) {

    companion object {
        /**
         * El backend exige imagenUrl al crear el producto; este valor
         * se reemplaza por la ruta real (/imagenes/...) apenas se sube el archivo.
         */
        private const val IMAGEN_PENDIENTE = "https://via.placeholder.com/300"

        private const val VENDEDOR_ID = 1L

        private val TIPOS_PERMITIDOS = setOf("image/jpeg", "image/jpg", "image/png")
    }

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

    private fun esExtensionPermitida(nombre: String): Boolean {
        val minusc = nombre.lowercase()
        return minusc.endsWith(".jpg") || minusc.endsWith(".jpeg") || minusc.endsWith(".png")
    }

    private fun formatoPermitido(uri: Uri): Boolean {
        val mime = resolver().getType(uri)?.lowercase().orEmpty()
        val nombre = nombreArchivo(uri)?.lowercase()

        // Si el proveedor no informa el nombre, se confía en el MIME
        val extensionOk = nombre == null || esExtensionPermitida(nombre)
        val mimeOk = mime.isBlank() || mime in TIPOS_PERMITIDOS

        return extensionOk && mimeOk
    }

    /**
     * Convierte el content:// de la galería en un RequestBody para Retrofit.
     */
    private fun cuerpoImagen(uri: Uri, mime: String): RequestBody {
        val tipo = if (mime.startsWith("image/")) mime else "application/octet-stream"
        val contentResolver = resolver()

        return object : RequestBody() {
            override fun contentType(): MediaType? = tipo.toMediaTypeOrNull()

            override fun contentLength(): Long = -1L

            override fun writeTo(sink: BufferedSink) {
                val entrada = contentResolver.openInputStream(uri)
                    ?: throw IOException("No se pudo leer la imagen seleccionada")

                entrada.use { stream ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val leido = stream.read(buffer)
                        if (leido == -1) break
                        sink.write(buffer, 0, leido)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Validación
    // ------------------------------------------------------------------

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

        if (imagenUri.isNullOrBlank()) {
            errores["imagen"] = "La imagen es obligatoria"
        }

        erroresCampo = errores
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
            try {
                // 1) Crear el producto
                val request = ProductoRequest(
                    nombre = nombre.trim(),
                    descripcion = descripcion.trim(),
                    precio = precio.toDouble(),
                    stock = stock.toInt(),
                    categoria = categoria.trim(),
                    estado = estado,
                    imagenUrl = IMAGEN_PENDIENTE,
                    vendedorId = VENDEDOR_ID
                )

                val respuesta =
                    RetrofitClient.productoApi.crearProducto(request)

                if (!respuesta.isSuccessful) {
                    uiState = PublicarUiState.Error(
                        mensajeDeError(
                            respuesta.code(),
                            respuesta.errorBody()?.string(),
                            "no se pudo registrar el producto"
                        )
                    )
                    return@launch
                }

                val idProducto = respuesta.body()?.id

                if (idProducto == null) {
                    uiState = PublicarUiState.Error(
                        "No se pudo obtener el ID del producto."
                    )
                    return@launch
                }

                // 2) Subir el archivo REAL de la imagen (multipart)
                val uri = Uri.parse(imagenUri)
                val mime = resolver().getType(uri).orEmpty()
                val nombre = nombreArchivo(uri) ?: "producto." +
                    if (mime == "image/png") "png" else "jpg"

                val parteArchivo = MultipartBody.Part.createFormData(
                    "file",
                    nombre,
                    cuerpoImagen(uri, mime)
                )

                val parteVendedor =
                    VENDEDOR_ID.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                val respuestaImagen =
                    RetrofitClient.productoApi.agregarImagen(
                        idProducto,
                        parteArchivo,
                        parteVendedor
                    )

                if (respuestaImagen.isSuccessful) {
                    uiState = PublicarUiState.Success(
                        "¡Producto e imagen registrados correctamente! (ID $idProducto)"
                    )
                    limpiarFormulario()
                } else {
                    uiState = PublicarUiState.Error(
                        mensajeDeError(
                            respuestaImagen.code(),
                            respuestaImagen.errorBody()?.string(),
                            "el producto fue creado, pero no se pudo subir la imagen"
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
        imagenUri = null
    }

    private fun mensajeDeError(codigo: Int, cuerpo: String?, accion: String): String {
        return try {
            val error = Gson().fromJson(
                cuerpo,
                ErrorResponse::class.java
            )

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
