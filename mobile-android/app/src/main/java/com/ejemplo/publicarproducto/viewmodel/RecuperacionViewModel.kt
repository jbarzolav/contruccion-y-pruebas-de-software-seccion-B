package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface RecuperacionUiState {
    data object Initial : RecuperacionUiState
    data object Loading : RecuperacionUiState
    data class Success(val mensaje: String) : RecuperacionUiState
    data class Error(val mensaje: String) : RecuperacionUiState
}

/** HU 22 - Validaciones, solicitud y restablecimiento delegados a AuthRepositorio. */
class RecuperacionViewModel : ViewModel() {
    var correo by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var confirmacion by mutableStateOf("")
        private set
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var uiState by mutableStateOf<RecuperacionUiState>(RecuperacionUiState.Initial)
        private set

    fun cambiarCorreo(valor: String) { correo = valor; limpiarError("correo") }
    fun cambiarPassword(valor: String) { password = valor; limpiarError("password") }
    fun cambiarConfirmacion(valor: String) { confirmacion = valor; limpiarError("confirmacion") }

    private fun limpiarError(campo: String) {
        errores = errores - campo
        if (uiState is RecuperacionUiState.Error) uiState = RecuperacionUiState.Initial
    }

    fun reiniciar() {
        correo = ""
        password = ""
        confirmacion = ""
        errores = emptyMap()
        uiState = RecuperacionUiState.Initial
    }

    fun enviar(token: String?) {
        if (uiState is RecuperacionUiState.Loading || uiState is RecuperacionUiState.Success) return
        val validaciones = mutableMapOf<String, String>()
        if (token == null) {
            if (correo.isBlank()) validaciones["correo"] = "El correo es obligatorio"
            else if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(correo.trim())) {
                validaciones["correo"] = "El correo debe tener un formato válido"
            }
        } else {
            if (token.isBlank()) validaciones["token"] = "El enlace no contiene un token válido."
            if (password.isBlank()) validaciones["password"] = "La contraseña es obligatoria"
            else if (password.toByteArray(Charsets.UTF_8).size > 72) {
                validaciones["password"] = "La contraseña supera el límite de 72 bytes."
            }
            if (confirmacion.isBlank()) validaciones["confirmacion"] = "Confirma la nueva contraseña"
            else if (password != confirmacion) validaciones["confirmacion"] = "Las contraseñas no coinciden."
        }
        errores = validaciones
        if (errores.isNotEmpty()) return
        uiState = RecuperacionUiState.Loading
        viewModelScope.launch {
            try {
                val auth = ProveedorDatos.actual.auth
                val resultado = if (token == null) {
                    auth.solicitarRecuperacion(correo.trim())
                } else {
                    auth.restablecer(token, password, confirmacion)
                }
                uiState = when (resultado) {
                    is ResultadoApi.Exito -> {
                        password = ""
                        confirmacion = ""
                        RecuperacionUiState.Success(resultado.valor.mensaje)
                    }

                    is ResultadoApi.Error -> RecuperacionUiState.Error(resultado.mensaje)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = RecuperacionUiState.Error("No se pudo completar la solicitud. Inténtalo de nuevo.")
            }
        }
    }
}
