package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.LoginResponse
import com.ejemplo.publicarproducto.model.RegistroRequest
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface RegistroUiState {
    data object Initial : RegistroUiState
    data object Loading : RegistroUiState
    data class Success(val mensaje: String, val sesion: LoginResponse? = null) : RegistroUiState
    data class Error(val mensaje: String) : RegistroUiState
}

/**
 * HU 26 - Registro de cuenta.
 *
 * Valida antes de llamar al backend, maneja 201/400/409 y errores de
 * red, y evita el doble envío. La contraseña solo vive en memoria y el
 * rol lo asigna siempre el backend (CLIENTE).
 */
class RegistroViewModel : ViewModel() {

    var nombre by mutableStateOf("")
        private set

    var correo by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var confirmacion by mutableStateOf("")
        private set

    var errorCorreo by mutableStateOf<String?>(null)
        private set

    var errorNombre by mutableStateOf<String?>(null)
        private set

    var errorPassword by mutableStateOf<String?>(null)
        private set

    var errorConfirmacion by mutableStateOf<String?>(null)
        private set

    var uiState by mutableStateOf<RegistroUiState>(RegistroUiState.Initial)
        private set

    // Corta el doble toque de forma SÍNCRONA, igual que el login.
    private var enviando = false

    fun cambiarCorreo(valor: String) {
        correo = valor
        errorCorreo = null
        limpiarErrorGeneral()
    }

    fun cambiarNombre(valor: String) {
        nombre = valor
        errorNombre = null
        limpiarErrorGeneral()
    }

    fun cambiarPassword(valor: String) {
        password = valor
        errorPassword = null
        limpiarErrorGeneral()
    }

    fun cambiarConfirmacion(valor: String) {
        confirmacion = valor
        errorConfirmacion = null
        limpiarErrorGeneral()
    }

    private fun limpiarErrorGeneral() {
        if (uiState is RegistroUiState.Error) {
            uiState = RegistroUiState.Initial
        }
    }

    fun reiniciar() {
        nombre = ""
        correo = ""
        password = ""
        confirmacion = ""
        errorNombre = null
        errorCorreo = null
        errorPassword = null
        errorConfirmacion = null
        uiState = RegistroUiState.Initial
    }

    fun registrar() {
        if (enviando || uiState is RegistroUiState.Success) return

        errorNombre = when {
            nombre.isBlank() -> "El nombre es obligatorio"
            nombre.trim().length > 100 -> "El nombre no puede superar 100 caracteres"
            else -> null
        }
        errorCorreo = when {
            correo.isBlank() -> "El correo es obligatorio"
            !CORREO_REGEX.matches(correo.trim()) ->
                "El correo debe tener un formato válido"
            else -> null
        }
        errorPassword = when {
            password.isBlank() -> "La contraseña es obligatoria"
            password.length < 8 -> "La contraseña debe tener al menos 8 caracteres"
            password.toByteArray(Charsets.UTF_8).size > 72 ->
                "La contraseña supera el límite de 72 bytes."
            else -> null
        }
        errorConfirmacion = when {
            confirmacion.isBlank() -> "Confirma la contraseña"
            password != confirmacion -> "Las contraseñas no coinciden."
            else -> null
        }

        if (errorNombre != null || errorCorreo != null || errorPassword != null || errorConfirmacion != null) return

        enviando = true
        uiState = RegistroUiState.Loading

        viewModelScope.launch {
            var cuentaCreada = false
            try {
                val auth = ProveedorDatos.actual.auth
                val registro = auth.registrar(
                    RegistroRequest(
                        nombre = nombre.trim(),
                        correo = correo.trim(),
                        password = password,
                        confirmacionPassword = confirmacion
                    )
                )
                uiState = when (registro) {
                    is ResultadoApi.Exito -> {
                        cuentaCreada = true
                        // HU 26 - Tras crear la cuenta se inicia sesión para
                        // entrar directo; AuthRepositorio persiste la sesión.
                        val login = auth.iniciarSesion(correo.trim(), password)
                        password = ""
                        confirmacion = ""
                        if (login is ResultadoApi.Exito) {
                            RegistroUiState.Success(registro.valor.mensaje, login.valor)
                        } else {
                            RegistroUiState.Success("Cuenta creada. Inicia sesión para continuar.")
                        }
                    }

                    is ResultadoApi.Error -> RegistroUiState.Error(registro.mensaje)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // La cuenta ya existe aunque falle la conexión al confirmarla.
                if (cuentaCreada) {
                    password = ""
                    confirmacion = ""
                    uiState = RegistroUiState.Success("Cuenta creada. Inicia sesión para continuar.")
                } else uiState = RegistroUiState.Error("Ocurrió un error inesperado. Inténtalo de nuevo.")
            } finally {
                enviando = false
            }
        }
    }

    companion object {
        // Mismo formato que el frontend web
        private val CORREO_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
