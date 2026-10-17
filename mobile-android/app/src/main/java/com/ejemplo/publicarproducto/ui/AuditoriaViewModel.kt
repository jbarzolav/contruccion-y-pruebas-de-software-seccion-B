package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.PaginaAuditoriaResponse
import com.ejemplo.publicarproducto.network.AdminApiClient
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response

sealed interface AuditoriaUiState {
    data object Initial : AuditoriaUiState
    data object Loading : AuditoriaUiState
    data class Success(val pagina: PaginaAuditoriaResponse) : AuditoriaUiState
    data class Error(val mensaje: String) : AuditoriaUiState
}

/** HU24 - La vista delega peticiones, paginación y errores al ViewModel. */
class AuditoriaViewModel : ViewModel() {
    var estado by mutableStateOf<AuditoriaUiState>(AuditoriaUiState.Initial)
        private set

    fun cargar(pagina: Int = 0) {
        if (estado is AuditoriaUiState.Loading || pagina < 0) return
        estado = AuditoriaUiState.Loading
        viewModelScope.launch {
            try {
                val respuesta = AdminApiClient.api.listarAuditoria(pagina, TAMANO_PAGINA)
                val body = respuesta.body()
                estado = if (respuesta.isSuccessful && body != null) {
                    AuditoriaUiState.Success(body)
                } else {
                    AuditoriaUiState.Error(mensajeError(respuesta))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                estado = AuditoriaUiState.Error("No hay conexión para consultar la auditoría.")
            }
        }
    }

    private fun mensajeError(respuesta: Response<*>): String = runCatching {
        Gson().fromJson(respuesta.errorBody()?.string(), ErrorResponse::class.java)
            ?.messages?.joinToString(" ")
    }.getOrNull() ?: when (respuesta.code()) {
        401 -> "Inicia sesión para consultar la auditoría."
        403 -> "No tienes permiso para consultar la auditoría."
        else -> "No se pudo cargar el historial de auditoría."
    }

    private companion object { const val TAMANO_PAGINA = 20 }
}
