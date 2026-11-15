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

/** HU 22 - Pasos del flujo de recuperación (mismo orden que el web). */
enum class PasoRecuperacion { CORREO, CODIGO, CONTRASENA }

sealed interface RecuperacionUiState {
    data object Initial : RecuperacionUiState
    data object Loading : RecuperacionUiState
    data class Success(val mensaje: String) : RecuperacionUiState
    data class Error(val mensaje: String) : RecuperacionUiState
}

/**
 * HU 22 - Recuperación en tres pasos, igual que el frontend web:
 *
 * 1. CORREO: se solicita el código de 6 dígitos (llega por correo).
 * 2. CODIGO: se verifica el código; no consume el código, solo lo valida.
 * 3. CONTRASENA: con el código verificado se guarda la nueva contraseña.
 *
 * El enlace del correo (pulgatec://restablecer?token=) trae ese mismo código
 * y se verifica automáticamente, igual que al abrir /restablecer en el web.
 */
class RecuperacionViewModel : ViewModel() {
    var paso by mutableStateOf(PasoRecuperacion.CORREO)
        private set
    var codigoVerificado by mutableStateOf(false)
        private set
    var correo by mutableStateOf("")
        private set
    var codigo by mutableStateOf("")
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

    /** Solo dígitos y tope de 6 (mismo formato que el web: maxLength=6). */
    fun cambiarCodigo(valor: String) {
        codigo = valor.filter { it.isDigit() }.take(6)
        limpiarError("codigo")
    }

    fun cambiarPassword(valor: String) { password = valor; limpiarError("password") }
    fun cambiarConfirmacion(valor: String) { confirmacion = valor; limpiarError("confirmacion") }

    private fun limpiarError(campo: String) {
        errores = errores - campo
        if (uiState is RecuperacionUiState.Error) uiState = RecuperacionUiState.Initial
    }

    /** Evita dobles envíos mientras hay una petición o un resultado en pantalla. */
    private fun ocupado() =
        uiState is RecuperacionUiState.Loading || uiState is RecuperacionUiState.Success

    fun reiniciar() {
        paso = PasoRecuperacion.CORREO
        codigoVerificado = false
        correo = ""
        codigo = ""
        password = ""
        confirmacion = ""
        errores = emptyMap()
        uiState = RecuperacionUiState.Initial
    }

    // ---------------------------------------------------------- paso CORREO
    fun solicitarCodigo() {
        if (ocupado()) return
        val validaciones = mutableMapOf<String, String>()
        if (correo.isBlank()) validaciones["correo"] = "El correo es obligatorio"
        else if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(correo.trim())) {
            validaciones["correo"] = "El correo debe tener un formato válido"
        }
        errores = validaciones
        if (errores.isNotEmpty()) return
        uiState = RecuperacionUiState.Loading
        viewModelScope.launch {
            try {
                val auth = ProveedorDatos.actual.auth
                uiState = when (val resultado = auth.solicitarRecuperacion(correo.trim())) {
                    is ResultadoApi.Exito -> RecuperacionUiState.Success(resultado.valor.mensaje)
                    is ResultadoApi.Error -> RecuperacionUiState.Error(resultado.mensaje)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = RecuperacionUiState.Error("No se pudo completar la solicitud. Inténtalo de nuevo.")
            }
        }
    }

    /** Del aviso de éxito se pasa al paso del código (web: "Ingresar el código"). */
    fun irAlCodigo() {
        paso = PasoRecuperacion.CODIGO
        codigo = ""
        codigoVerificado = false
        errores = emptyMap()
        uiState = RecuperacionUiState.Initial
    }

    /** "Solicitar un nuevo código": se vuelve al paso del correo. */
    fun volverAlCorreo() {
        paso = PasoRecuperacion.CORREO
        codigo = ""
        codigoVerificado = false
        password = ""
        confirmacion = ""
        errores = emptyMap()
        uiState = RecuperacionUiState.Initial
    }

    // ---------------------------------------------------------- paso CODIGO
    fun verificarCodigo() {
        if (ocupado()) return
        val limpio = codigo.trim()
        if (limpio.isEmpty()) {
            errores = mapOf("codigo" to "Ingresa el código de verificación que enviamos a tu correo.")
            return
        }
        errores = emptyMap()
        uiState = RecuperacionUiState.Loading
        viewModelScope.launch {
            try {
                val auth = ProveedorDatos.actual.auth
                when (val resultado = auth.verificarCodigo(limpio)) {
                    is ResultadoApi.Exito -> {
                        codigo = limpio
                        codigoVerificado = true
                        paso = PasoRecuperacion.CONTRASENA
                        uiState = RecuperacionUiState.Initial
                    }

                    is ResultadoApi.Error -> {
                        codigoVerificado = false
                        errores = mapOf("codigo" to resultado.mensaje)
                        uiState = RecuperacionUiState.Initial
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errores = mapOf("codigo" to "No se pudo verificar el código. Inténtalo de nuevo.")
                uiState = RecuperacionUiState.Initial
            }
        }
    }

    /**
     * Enlace del correo: el parámetro `token` ES el código de 6 dígitos, así
     * que se verifica automáticamente igual que el web al abrir /restablecer.
     */
    fun validarEnlace(token: String?) {
        if (token == null) return
        if (token.isBlank()) {
            // Enlace sin código: se abre el paso del código vacío.
            paso = PasoRecuperacion.CODIGO
            codigo = ""
            return
        }
        cambiarCodigo(token)
        verificarCodigo()
    }

    /** "Usar otro código": se vuelve al paso del código (mantiene el valor). */
    fun usarOtroCodigo() {
        paso = PasoRecuperacion.CODIGO
        codigoVerificado = false
        password = ""
        confirmacion = ""
        errores = emptyMap()
        uiState = RecuperacionUiState.Initial
    }

    // ------------------------------------------------------- paso CONTRASENA
    fun guardarPassword() {
        if (ocupado()) return
        val validaciones = mutableMapOf<String, String>()
        if (password.isBlank()) validaciones["password"] = "La contraseña es obligatoria"
        else if (password.toByteArray(Charsets.UTF_8).size > 72) {
            validaciones["password"] = "La contraseña supera el límite de 72 bytes."
        }
        if (confirmacion.isBlank()) validaciones["confirmacion"] = "Confirma la nueva contraseña"
        else if (password != confirmacion) validaciones["confirmacion"] = "Las contraseñas no coinciden."
        errores = validaciones
        if (errores.isNotEmpty()) return
        uiState = RecuperacionUiState.Loading
        viewModelScope.launch {
            try {
                val auth = ProveedorDatos.actual.auth
                // El backend recibe el código verificado como token.
                uiState = when (val resultado = auth.restablecer(codigo.trim(), password, confirmacion)) {
                    is ResultadoApi.Exito -> {
                        password = ""
                        confirmacion = ""
                        RecuperacionUiState.Success(resultado.valor.mensaje)
                    }

                    is ResultadoApi.Error -> {
                        val texto = resultado.mensaje
                        // Si el código dejó de servir (venció o se agotaron los
                        // intentos), se vuelve al paso del código (igual que el web).
                        if (Regex("c[oó]digo", RegexOption.IGNORE_CASE).containsMatchIn(texto)) {
                            paso = PasoRecuperacion.CODIGO
                            codigoVerificado = false
                            errores = mapOf("codigo" to texto)
                        }
                        RecuperacionUiState.Error(texto)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = RecuperacionUiState.Error("No se pudo completar la solicitud. Inténtalo de nuevo.")
            }
        }
    }
}
