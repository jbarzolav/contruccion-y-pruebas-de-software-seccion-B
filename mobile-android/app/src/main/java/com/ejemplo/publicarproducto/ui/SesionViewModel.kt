package com.ejemplo.publicarproducto.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.network.UserApiClient
import com.ejemplo.publicarproducto.session.SessionManager
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response

sealed interface LogoutUiState {
    data object Initial : LogoutUiState
    data object Loading : LogoutUiState
    data object Success : LogoutUiState
    data class Error(val mensaje: String) : LogoutUiState
}

/** HU 23 - Permisos actuales, validación al entrar y avisos de autorización. */
class SesionViewModel(application: Application) : AndroidViewModel(application) {
    val sesion = SessionManager(application)
    var usuario by mutableStateOf(sesion.obtenerUsuario())
        private set
    var validando by mutableStateOf(sesion.haySesion())
        private set
    var aviso by mutableStateOf<String?>(null)
        private set
    var logoutState by mutableStateOf<LogoutUiState>(LogoutUiState.Initial)
        private set

    fun recargarYValidar() {
        usuario = sesion.obtenerUsuario()
        aviso = null
        validando = usuario != null
        actualizar()
    }
    fun actualizar() {
        val token = sesion.obtenerToken() ?: run { validando = false; return }
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.authApi.obtenerSesion()
                if (sesion.obtenerToken() != token) return@launch
                val actual = respuesta.body()
                if (respuesta.isSuccessful && actual != null) {
                    sesion.actualizarUsuario(actual)
                    usuario = actual
                } else if (respuesta.code() == 401) manejarError(401)
                else aviso = "No se pudieron verificar los permisos. Inténtalo de nuevo."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { aviso = "No hay conexión para verificar la sesión." }
            finally { validando = false }
        }
    }
    fun manejarError(codigo: Int) {
        if (codigo == 401) {
            sesion.invalidarSesion()
            usuario = null
            validando = false
            aviso = "Tu sesión ha vencido o es inválida. Inicia sesión nuevamente."
        } else {
            aviso = "No tienes permiso para realizar esta acción."
            actualizar()
        }
    }
    fun volverAlLogin() {
        sesion.invalidarSesion()
        usuario = null
        validando = false
        aviso = null
    }

    fun reiniciarEstadoLogout() { logoutState = LogoutUiState.Initial }

    /** Revoca en servidor con la sesión existente y siempre limpia datos locales al terminar. */
    fun cerrarSesion() {
        if (logoutState is LogoutUiState.Loading) return
        val token = sesion.obtenerToken()
        if (token.isNullOrBlank()) {
            sesion.invalidarSesion()
            usuario = null
            validando = false
            logoutState = LogoutUiState.Success
            return
        }
        logoutState = LogoutUiState.Loading
        viewModelScope.launch {
            var resultado: LogoutUiState = LogoutUiState.Success
            try {
                val respuesta = UserApiClient.authApi.cerrarSesion()
                if (!respuesta.isSuccessful && respuesta.code() != 401) {
                    resultado = LogoutUiState.Error(mensajeErrorLogout(respuesta))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                resultado = LogoutUiState.Error("No se pudo confirmar el cierre en el servidor; se cerró la sesión local.")
            } finally {
                sesion.invalidarSesion()
                usuario = null
                validando = false
                logoutState = resultado
            }
        }
    }

    private fun mensajeErrorLogout(respuesta: Response<*>): String = runCatching {
        Gson().fromJson(respuesta.errorBody()?.string(), ErrorResponse::class.java)
            ?.messages?.joinToString(" ")
    }.getOrNull() ?: "No se pudo confirmar el cierre; se cerró la sesión local."

    fun cerrarAviso() { aviso = null }
}
