package com.ejemplo.publicarproducto.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ActualizarPerfilRequest
import com.ejemplo.publicarproducto.model.PerfilUsuarioResponse
import com.ejemplo.publicarproducto.model.PerfilVendedorRequest
import com.ejemplo.publicarproducto.model.PerfilVendedorResponse
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.network.UserApiClient
import com.ejemplo.publicarproducto.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException

sealed interface PerfilUiState {
    data object Cargando : PerfilUiState
    data class Exito(val perfil: PerfilUsuarioResponse) : PerfilUiState
    data class Error(val mensaje: String) : PerfilUiState
}

/**
 * HU 27 - ViewModel para la pantalla de perfil Android.
 *
 * HU 28 - Agrega el modo edición de los campos personales habilitados
 * (nombre, correo, teléfono) con validación local, doble toque cortado,
 * y refresco del perfil con la respuesta del backend. La identidad la
 * decide el Bearer de SessionManager: el body nunca envía id ni rol.
 *
 * HU 29 - Carga y guarda el perfil comercial cuando la sesión es VENDEDOR.
 */
class PerfilViewModel(application: Application) : AndroidViewModel(application) {
    val sesion = SessionManager(application)
    var estado by mutableStateOf<PerfilUiState>(PerfilUiState.Cargando)
        private set

    // ------------------------------------------------------------
    // HU 28 - edición de datos personales
    // ------------------------------------------------------------
    var editando by mutableStateOf(false)
        private set
    var nombre by mutableStateOf("")
        private set
    var correo by mutableStateOf("")
        private set
    var telefono by mutableStateOf("")
        private set
    var errorNombre by mutableStateOf<String?>(null)
        private set
    var errorCorreo by mutableStateOf<String?>(null)
        private set
    var errorTelefono by mutableStateOf<String?>(null)
        private set
    var guardando by mutableStateOf(false)
        private set
    var avisoEdicion by mutableStateOf<String?>(null)
        private set

    // Corta el doble toque de forma SÍNCRONA, igual que registro/login.
    private var enviandoEdicion = false

    // HU 30 - selección, validación y carga de foto de perfil.
    var fotoLocalUri by mutableStateOf<Uri?>(null)
        private set
    var subiendoFoto by mutableStateOf(false)
        private set
    var avisoFoto by mutableStateOf<String?>(null)
        private set
    private var enviandoFoto = false

    fun seleccionarFoto(uri: Uri?) {
        if (uri == null) return
        fotoLocalUri = uri
        avisoFoto = null
    }

    fun subirFoto() {
        if (enviandoFoto) return
        val uri = fotoLocalUri ?: return
        val application = getApplication<Application>()
        val tipo = application.contentResolver.getType(uri)
        val extension = when (tipo?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            else -> {
                avisoFoto = "Selecciona una imagen JPG o PNG."
                return
            }
        }

        enviandoFoto = true
        subiendoFoto = true
        avisoFoto = null
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    application.contentResolver.openInputStream(uri)?.use { input ->
                        val salida = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val leidos = input.read(buffer)
                            if (leidos < 0) break
                            total += leidos
                            if (total > MAX_FOTO_BYTES) break
                            salida.write(buffer, 0, leidos)
                        }
                        if (total > MAX_FOTO_BYTES) null else salida.toByteArray()
                    }
                }
                if (bytes == null) {
                    avisoFoto = "La imagen no debe superar 2 MB."
                    return@launch
                }
                if (bytes.isEmpty()) {
                    avisoFoto = "No se pudo leer la imagen seleccionada."
                    return@launch
                }

                val mediaType = if (extension == "png") "image/png" else "image/jpeg"
                val archivo = MultipartBody.Part.createFormData(
                    "file", "foto-perfil.$extension", bytes.toRequestBody(mediaType.toMediaTypeOrNull())
                )
                val respuesta = UserApiClient.authApi.actualizarFotoPerfil(archivo)
                when {
                    respuesta.isSuccessful && respuesta.body() != null -> {
                        estado = PerfilUiState.Exito(respuesta.body()!!)
                        fotoLocalUri = null
                        avisoFoto = "Foto de perfil actualizada correctamente."
                    }
                    respuesta.code() == 401 -> {
                        sesion.invalidarSesion()
                        estado = PerfilUiState.Error("Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.")
                    }
                    respuesta.code() == 400 || respuesta.code() == 413 ->
                        avisoFoto = "La imagen debe ser JPG o PNG y no superar 2 MB."
                    else -> avisoFoto = "No se pudo actualizar la foto (HTTP ${respuesta.code()})."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                avisoFoto = "No hay conexión con el servidor o no se pudo leer la imagen."
            } catch (e: Exception) {
                avisoFoto = "No se pudo actualizar la foto. Inténtalo de nuevo."
            } finally {
                enviandoFoto = false
                subiendoFoto = false
            }
        }
    }

    // ------------------------------------------------------------
    // HU 29 - perfil comercial del vendedor
    // ------------------------------------------------------------
    var cargandoVendedor by mutableStateOf(false)
        private set
    var perfilVendedor by mutableStateOf<PerfilVendedorResponse?>(null)
        private set
    var editandoVendedor by mutableStateOf(false)
        private set
    var nombreComercial by mutableStateOf("")
        private set
    var descripcion by mutableStateOf("")
        private set
    var telefonoContacto by mutableStateOf("")
        private set
    var errorNombreComercial by mutableStateOf<String?>(null)
        private set
    var errorDescripcion by mutableStateOf<String?>(null)
        private set
    var errorTelefonoContacto by mutableStateOf<String?>(null)
        private set
    var guardandoVendedor by mutableStateOf(false)
        private set
    var avisoVendedor by mutableStateOf<String?>(null)
        private set

    private var enviandoVendedor = false

    fun cargarPerfil() {
        estado = PerfilUiState.Cargando
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.authApi.obtenerPerfil()
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val perfil = respuesta.body()!!
                    estado = PerfilUiState.Exito(perfil)
                    if (perfil.rol == "VENDEDOR") cargarPerfilVendedor()
                } else if (respuesta.code() == 401) {
                    sesion.invalidarSesion()
                    estado = PerfilUiState.Error("Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.")
                } else {
                    estado = PerfilUiState.Error("No se pudo cargar el perfil.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                estado = PerfilUiState.Error("No se pudo conectar con el servidor.")
            }
        }
    }

    // ============================================================
    // HU 28 - editar datos personales
    // ============================================================
    fun iniciarEdicion() {
        val perfil = (estado as? PerfilUiState.Exito)?.perfil ?: return
        nombre = perfil.nombre.orEmpty()
        correo = perfil.correo
        telefono = perfil.telefono.orEmpty()
        errorNombre = null
        errorCorreo = null
        errorTelefono = null
        avisoEdicion = null
        editando = true
    }

    /** HU 28 - Cancelar restaura la vista con los datos previos (rollback visual). */
    fun cancelarEdicion() {
        val perfil = (estado as? PerfilUiState.Exito)?.perfil ?: return
        nombre = perfil.nombre.orEmpty()
        correo = perfil.correo
        telefono = perfil.telefono.orEmpty()
        errorNombre = null
        errorCorreo = null
        errorTelefono = null
        avisoEdicion = null
        editando = false
    }

    fun cambiarNombre(valor: String) {
        nombre = valor
        errorNombre = null
        avisoEdicion = null
    }

    fun cambiarCorreo(valor: String) {
        correo = valor
        errorCorreo = null
        avisoEdicion = null
    }

    fun cambiarTelefono(valor: String) {
        telefono = valor
        errorTelefono = null
        avisoEdicion = null
    }

    fun guardarCambios() {
        if (enviandoEdicion) return

        errorNombre = when {
            nombre.isBlank() -> "El nombre es obligatorio"
            nombre.trim().length > 100 -> "El nombre no puede superar 100 caracteres"
            else -> null
        }
        errorCorreo = when {
            correo.isBlank() -> "El correo es obligatorio"
            !CORREO_REGEX.matches(correo.trim()) -> "El correo debe tener un formato válido"
            else -> null
        }
        errorTelefono = when {
            telefono.isNotBlank() && !TELEFONO_REGEX.matches(telefono.trim()) ->
                "El teléfono debe contener entre 7 y 15 dígitos (se permite +, espacios y guiones)"
            else -> null
        }
        if (errorNombre != null || errorCorreo != null || errorTelefono != null) return

        enviandoEdicion = true
        guardando = true
        avisoEdicion = null

        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.authApi.actualizarPerfil(
                    ActualizarPerfilRequest(
                        nombre = nombre.trim(),
                        correo = correo.trim(),
                        telefono = telefono.trim().ifEmpty { null }
                    )
                )
                when {
                    respuesta.isSuccessful && respuesta.body() != null -> {
                        val perfil = respuesta.body()!!
                        estado = PerfilUiState.Exito(perfil)
                        // La sesión local refleja el correo por si cambió.
                        sesion.actualizarUsuario(
                            UsuarioRolResponse(perfil.id, perfil.correo, perfil.rol, perfil.permisos)
                        )
                        editando = false
                        avisoEdicion = "Datos actualizados correctamente."
                    }

                    respuesta.code() == 409 -> errorCorreo = "El correo ya está registrado por otra cuenta"
                    respuesta.code() == 400 -> avisoEdicion =
                        "Revisa los datos ingresados: algunos campos son inválidos."

                    respuesta.code() == 401 -> {
                        sesion.invalidarSesion()
                        estado = PerfilUiState.Error("Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.")
                    }

                    else -> avisoEdicion = "No se pudo actualizar el perfil (HTTP ${respuesta.code()})."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                avisoEdicion = "No hay conexión con el servidor."
            } catch (e: Exception) {
                avisoEdicion = "Ocurrió un error inesperado. Inténtalo de nuevo."
            } finally {
                enviandoEdicion = false
                guardando = false
            }
        }
    }

    // ============================================================
    // HU 29 - perfil comercial del vendedor
    // ============================================================
    fun cargarPerfilVendedor() {
        cargandoVendedor = true
        avisoVendedor = null
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.authApi.obtenerPerfilVendedor()
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    perfilVendedor = respuesta.body()
                } else if (respuesta.code() == 401) {
                    sesion.invalidarSesion()
                    estado = PerfilUiState.Error("Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.")
                } else if (respuesta.code() == 403) {
                    avisoVendedor = "Solo los vendedores pueden gestionar esta información."
                } else {
                    avisoVendedor = "No se pudo cargar la información del vendedor."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                avisoVendedor = "No hay conexión con el servidor."
            } catch (e: Exception) {
                avisoVendedor = "No se pudo cargar la información del vendedor."
            } finally {
                cargandoVendedor = false
            }
        }
    }

    fun iniciarEdicionVendedor() {
        nombreComercial = perfilVendedor?.nombreComercial.orEmpty()
        descripcion = perfilVendedor?.descripcion.orEmpty()
        telefonoContacto = perfilVendedor?.telefonoContacto.orEmpty()
        errorNombreComercial = null
        errorDescripcion = null
        errorTelefonoContacto = null
        avisoVendedor = null
        editandoVendedor = true
    }

    fun cancelarEdicionVendedor() {
        editandoVendedor = false
        errorNombreComercial = null
        errorDescripcion = null
        errorTelefonoContacto = null
        avisoVendedor = null
    }

    fun cambiarNombreComercial(valor: String) {
        nombreComercial = valor
        errorNombreComercial = null
        avisoVendedor = null
    }

    fun cambiarDescripcion(valor: String) {
        descripcion = valor
        errorDescripcion = null
        avisoVendedor = null
    }

    fun cambiarTelefonoContacto(valor: String) {
        telefonoContacto = valor
        errorTelefonoContacto = null
        avisoVendedor = null
    }

    fun guardarPerfilVendedor() {
        if (enviandoVendedor) return

        errorNombreComercial = when {
            nombreComercial.isBlank() -> "El nombre comercial es obligatorio"
            nombreComercial.trim().length > 80 -> "El nombre comercial no puede superar 80 caracteres"
            else -> null
        }
        errorDescripcion = when {
            descripcion.isBlank() -> "La descripción es obligatoria"
            descripcion.trim().length > 500 -> "La descripción no puede superar 500 caracteres"
            else -> null
        }
        errorTelefonoContacto = when {
            telefonoContacto.isBlank() -> "El teléfono de contacto es obligatorio"
            !TELEFONO_REGEX.matches(telefonoContacto.trim()) ->
                "El teléfono debe contener entre 7 y 15 dígitos (se permite +, espacios y guiones)"
            else -> null
        }
        if (errorNombreComercial != null || errorDescripcion != null || errorTelefonoContacto != null) return

        enviandoVendedor = true
        guardandoVendedor = true
        avisoVendedor = null

        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.authApi.guardarPerfilVendedor(
                    PerfilVendedorRequest(
                        nombreComercial = nombreComercial.trim(),
                        descripcion = descripcion.trim(),
                        telefonoContacto = telefonoContacto.trim()
                    )
                )
                when {
                    respuesta.isSuccessful && respuesta.body() != null -> {
                        perfilVendedor = respuesta.body()
                        editandoVendedor = false
                        avisoVendedor = "Información del vendedor guardada correctamente."
                    }

                    respuesta.code() == 400 -> avisoVendedor =
                        "Revisa los datos ingresados: algunos campos son inválidos."

                    respuesta.code() == 401 -> {
                        sesion.invalidarSesion()
                        estado = PerfilUiState.Error("Tu sesión ha vencido o es inválida. Inicia sesión nuevamente.")
                    }

                    respuesta.code() == 403 -> avisoVendedor =
                        "Solo los vendedores pueden gestionar esta información."

                    else -> avisoVendedor = "No se pudo guardar (HTTP ${respuesta.code()})."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                avisoVendedor = "No hay conexión con el servidor."
            } catch (e: Exception) {
                avisoVendedor = "Ocurrió un error inesperado. Inténtalo de nuevo."
            } finally {
                enviandoVendedor = false
                guardandoVendedor = false
            }
        }
    }

    companion object {
        private const val MAX_FOTO_BYTES = 2 * 1024 * 1024
        // Mismos formatos que el frontend web y el backend Spring.
        private val CORREO_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        private val TELEFONO_REGEX = Regex("^\\+?[0-9][0-9 \\-]{5,17}[0-9]$")
    }
}
