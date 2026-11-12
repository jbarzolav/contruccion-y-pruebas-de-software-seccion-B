package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.LoginResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Initial : LoginUiState
    data object Loading : LoginUiState
    data class Success(val respuesta: LoginResponse) : LoginUiState
    data class Error(val mensaje: String) : LoginUiState
}

/**
 * HU 21 - inicio de sesión seguro.
 *
 * Valida antes de llamar al backend, maneja 200/400/401 y errores de
 * red, y evita el doble envío. La contraseña solo vive en memoria:
 * jamás se guarda ni se registra en logs (UserApiClient usa un
 * cliente sin logging interceptor para auth).
 */
class LoginViewModel : ViewModel() {

    var correo by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var errorCorreo by mutableStateOf<String?>(null)
        private set

    var errorPassword by mutableStateOf<String?>(null)
        private set

    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Initial)
        private set

    // Corta el doble toque de forma SÍNCRONA: aunque dos toques lleguen
    // antes de que Compose repinte el botón deshabilitado, solo sale uno
    private var enviando = false

    fun cambiarCorreo(valor: String) {
        correo = valor
        errorCorreo = null
        limpiarErrorGeneral()
    }

    fun cambiarPassword(valor: String) {
        password = valor
        errorPassword = null
        limpiarErrorGeneral()
    }

    private fun limpiarErrorGeneral() {
        if (uiState is LoginUiState.Error) {
            uiState = LoginUiState.Initial
        }
    }

    // HU 23 - Limpiar el resultado al entrar al flujo principal.
    fun consumirExito() { password = ""; uiState = LoginUiState.Initial }

    fun iniciarSesion() {
        if (enviando) return

        // Validación local antes de tocar la red (mensajes iguales al web)
        errorCorreo = when {
            correo.isBlank() -> "El correo es obligatorio"
            !CORREO_REGEX.matches(correo.trim()) ->
                "El correo debe tener un formato válido"
            else -> null
        }
        errorPassword =
            if (password.isBlank()) "La contraseña es obligatoria" else null

        if (errorCorreo != null || errorPassword != null) return

        enviando = true
        uiState = LoginUiState.Loading

        viewModelScope.launch {
            try {
                // MVVM: la llamada y la persistencia de la sesión viven en
                // AuthRepositorio; el ViewModel solo traduce a estado de UI.
                val resultado = ProveedorDatos.actual.auth.iniciarSesion(
                    correo = correo.trim(),
                    password = password
                )
                uiState = when (resultado) {
                    is ResultadoApi.Exito -> LoginUiState.Success(resultado.valor)
                    is ResultadoApi.Error -> LoginUiState.Error(resultado.mensaje)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = LoginUiState.Error(
                    "Ocurrió un error inesperado. Inténtalo de nuevo."
                )
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
