package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.PaginaAuditoriaResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface AuditoriaUiState {
    data object Initial : AuditoriaUiState
    data object Loading : AuditoriaUiState
    data class Success(val pagina: PaginaAuditoriaResponse) : AuditoriaUiState
    data class Error(val mensaje: String) : AuditoriaUiState
}

/** HU24 - La vista delega peticiones, paginación y errores al ViewModel. */
class AuditoriaViewModel : ViewModel() {
    // MVVM: la auditoría vive en AdminRepositorio (Django :8001).
    private val admin get() = ProveedorDatos.actual.admin
    var estado by mutableStateOf<AuditoriaUiState>(AuditoriaUiState.Initial)
        private set

    fun cargar(pagina: Int = 0) {
        if (estado is AuditoriaUiState.Loading || pagina < 0) return
        estado = AuditoriaUiState.Loading
        viewModelScope.launch {
            try {
                estado = when (val resultado = admin.listarAuditoria(pagina, TAMANO_PAGINA)) {
                    is ResultadoApi.Exito -> AuditoriaUiState.Success(resultado.valor)
                    is ResultadoApi.Error -> AuditoriaUiState.Error(resultado.mensaje)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                estado = AuditoriaUiState.Error("No hay conexión para consultar la auditoría.")
            }
        }
    }

    private companion object { const val TAMANO_PAGINA = 20 }
}
