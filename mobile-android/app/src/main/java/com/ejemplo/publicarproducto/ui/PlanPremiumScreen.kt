package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PlanPremiumScreen(
    modifier: Modifier = Modifier,
    viewModel: PlanPremiumViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.cargarPlan()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Plan Premium de PulgaTec",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        when (val estado = viewModel.uiState) {
            PlanPremiumUiState.Loading -> {
                CircularProgressIndicator()
                Text("Cargando información del plan...")
            }

            is PlanPremiumUiState.Error -> {
                Text(
                    text = estado.mensaje,
                    color = MaterialTheme.colorScheme.error
                )

                Button(onClick = viewModel::cargarPlan) {
                    Text("Reintentar")
                }
            }

            PlanPremiumUiState.Success -> {
                val plan = viewModel.plan

                if (plan != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = plan.nombre,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Text(plan.descripcion)

                            Text(
                                text = "Beneficios del plan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            plan.beneficios.forEach { beneficio ->
                                Text("✓ $beneficio")
                            }

                            Button(
                                onClick = viewModel::seleccionarPlan,
                                enabled = !viewModel.seleccionado,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (viewModel.seleccionado)
                                        "Plan seleccionado"
                                    else
                                        "Seleccionar plan Premium"
                                )
                            }

                            if (viewModel.seleccionado) {
                                Text(
                                    "Has seleccionado el plan Premium para conocer sus beneficios. " +
                                    "Esta selección no activa una suscripción ni genera pagos.",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
