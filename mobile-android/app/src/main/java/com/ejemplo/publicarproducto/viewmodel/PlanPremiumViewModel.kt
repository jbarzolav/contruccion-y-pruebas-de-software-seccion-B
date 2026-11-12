package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
import com.ejemplo.publicarproducto.model.PlanPremiumResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.launch

// HU 18 - Estados de la consulta del plan Premium.
sealed interface PlanPremiumUiState {
    data object Loading : PlanPremiumUiState
    data object Success : PlanPremiumUiState
    data class Error(val mensaje: String) : PlanPremiumUiState
}

/**
 * HU 18 - Consulta del plan Premium.
 *
 * El ViewModel no habla con Retrofit: delega en [ProductosRepositorio].
 */
class PlanPremiumViewModel : ViewModel() {

    /** Repositorio de productos (se resuelve en cada llamada). */
    private val repositorio: ProductosRepositorio
        get() = ProveedorDatos.actual.productos

    var uiState by mutableStateOf<PlanPremiumUiState>(
        PlanPremiumUiState.Loading
    )
        private set

    var plan by mutableStateOf<PlanPremiumResponse?>(null)
        private set

    var seleccionado by mutableStateOf(false)
        private set

    fun cargarPlan() {
        uiState = PlanPremiumUiState.Loading

        viewModelScope.launch {
            when (val resultado = repositorio.consultarPlanPremium()) {
                is ResultadoApi.Exito -> {
                    plan = resultado.valor
                    uiState = PlanPremiumUiState.Success
                }

                is ResultadoApi.Error -> {
                    plan = null
                    uiState = PlanPremiumUiState.Error(resultado.mensaje)
                }
            }
        }
    }

    // Selección informativa: no activa suscripciones ni realiza pagos.
    fun seleccionarPlan() {
        if (uiState == PlanPremiumUiState.Success && plan != null) {
            seleccionado = true
        }
    }
}
