package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.PlanPremiumResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import kotlinx.coroutines.launch

// HU 18 - Estados de la consulta del plan Premium.
sealed interface PlanPremiumUiState {
    data object Loading : PlanPremiumUiState
    data object Success : PlanPremiumUiState
    data class Error(val mensaje: String) : PlanPremiumUiState
}

class PlanPremiumViewModel : ViewModel() {

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
            try {
                val respuesta =
                    RetrofitClient.productoApi.consultarPlanPremium()

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    plan = respuesta.body()
                    uiState = PlanPremiumUiState.Success
                } else {
                    plan = null
                    uiState = PlanPremiumUiState.Error(
                        "No se pudo consultar el plan Premium. " +
                        "Error HTTP ${respuesta.code()}."
                    )
                }
            } catch (e: Exception) {
                plan = null
                uiState = PlanPremiumUiState.Error(
                    "No hay conexión con el backend. " +
                    "Verifica que Spring Boot esté encendido."
                )
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
