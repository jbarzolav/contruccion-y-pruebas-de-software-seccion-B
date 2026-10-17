package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.LoginRequest
import com.ejemplo.publicarproducto.model.LoginResponse
import com.ejemplo.publicarproducto.network.UserApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException

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
                val respuesta = UserApiClient.authApi.login(
                    LoginRequest(correo = correo.trim(), password = password)
                )

                uiState = when {
                    respuesta.isSuccessful && respuesta.body() != null ->
                        LoginUiState.Success(respuesta.body()!!)

                    respuesta.code() == 401 ->
                        // Mismo mensaje genérico: no revela si el correo existe
                        LoginUiState.Error("Correo o contraseña incorrectos.")

                    respuesta.code() == 400 ->
                        LoginUiState.Error(mensajeDeValidacion(respuesta))

                    else ->
                        LoginUiState.Error(
                            "Ocurrió un error (HTTP ${respuesta.code()}). Inténtalo de nuevo."
                        )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                uiState = LoginUiState.Error("No hay conexión con el servidor.")
            } catch (e: Exception) {
                uiState = LoginUiState.Error(
                    "Ocurrió un error inesperado. Inténtalo de nuevo."
                )
            } finally {
                enviando = false
            }
        }
    }

    /** Lee {status, error, messages} del backend cuando la validación falla (400). */
    private fun mensajeDeValidacion(respuesta: Response<*>): String {
        return try {
            val cuerpo = respuesta.errorBody()?.string()
            val error = cuerpo?.let {
                Gson().fromJson(it, ErrorResponse::class.java)
            }
            error?.messages?.joinToString(" ")
                ?: "Revisa los datos ingresados."
        } catch (e: Exception) {
            "Revisa los datos ingresados."
        }
    }

    companion object {
        // Mismo formato que el frontend web
        private val CORREO_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
