package com.ejemplo.publicarproducto.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.network.UserApiClient
import com.ejemplo.publicarproducto.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface PerfilUiState {
    data object Cargando : PerfilUiState
    data class Exito(val perfil: UsuarioRolResponse) : PerfilUiState
    data class Error(val mensaje: String) : PerfilUiState
}

/** HU 27 - ViewModel para la pantalla de perfil Android. */
class PerfilViewModel(application: Application) : AndroidViewModel(application) {
    val sesion = SessionManager(application)
    var estado by mutableStateOf<PerfilUiState>(PerfilUiState.Cargando)
        private set

    fun cargarPerfil() {
        estado = PerfilUiState.Cargando
        viewModelScope.launch {
            try {
                val respuesta = UserApiClient.authApi.obtenerPerfil()
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    estado = PerfilUiState.Exito(respuesta.body()!!)
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
}
