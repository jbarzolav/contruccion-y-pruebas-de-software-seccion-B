package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.RegistroRequest
import com.ejemplo.publicarproducto.network.UserApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException

sealed interface RegistroUiState {
    data object Initial : RegistroUiState
    data object Loading : RegistroUiState
    data class Success(val mensaje: String) : RegistroUiState
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

    var correo by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var confirmacion by mutableStateOf("")
        private set

    var errorCorreo by mutableStateOf<String?>(null)
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
        correo = ""
        password = ""
        confirmacion = ""
        errorCorreo = null
        errorPassword = null
        errorConfirmacion = null
        uiState = RegistroUiState.Initial
    }

    fun registrar() {
        if (enviando || uiState is RegistroUiState.Success) return

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

        if (errorCorreo != null || errorPassword != null || errorConfirmacion != null) return

        enviando = true
        uiState = RegistroUiState.Loading

        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.authApi.registro(
                    RegistroRequest(
                        correo = correo.trim(),
                        password = password,
                        confirmacionPassword = confirmacion
                    )
                )
                val cuerpo = respuesta.body()
                uiState = when {
                    respuesta.isSuccessful && cuerpo != null -> {
                        // La contraseña no se conserva tras crear la cuenta.
                        password = ""
                        confirmacion = ""
                        RegistroUiState.Success(cuerpo.mensaje)
                    }

                    // 409 correo duplicado y 400 validaciones traen {status, error, messages}.
                    respuesta.code() == 409 || respuesta.code() == 400 ->
                        RegistroUiState.Error(mensajeDeValidacion(respuesta))

                    else ->
                        RegistroUiState.Error(
                            "Ocurrió un error (HTTP ${respuesta.code()}). Inténtalo de nuevo."
                        )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                uiState = RegistroUiState.Error("No hay conexión con el servidor.")
            } catch (e: Exception) {
                uiState = RegistroUiState.Error("Ocurrió un error inesperado. Inténtalo de nuevo.")
            } finally {
                enviando = false
            }
        }
    }

    /** Lee {status, error, messages} del backend cuando el registro falla. */
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
