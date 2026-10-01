package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val ESTADOS = listOf("DISPONIBLE", "AGOTADO", "INACTIVO")

@Composable
fun PublicarProductoScreen(
    modifier: Modifier = Modifier,
    viewModel: PublicarProductoViewModel = viewModel()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Publicar producto",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // -------- Campos --------
        OutlinedTextField(
            value = viewModel.nombre,
            onValueChange = viewModel::onNombreChange,
            label = { Text("Nombre *") },
            isError = viewModel.erroresCampo.containsKey("nombre"),
            supportingText = { viewModel.erroresCampo["nombre"]?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = viewModel.descripcion,
            onValueChange = viewModel::onDescripcionChange,
            label = { Text("Descripción *") },
            isError = viewModel.erroresCampo.containsKey("descripcion"),
            supportingText = { viewModel.erroresCampo["descripcion"]?.let { Text(it) } },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = viewModel.precio,
                onValueChange = viewModel::onPrecioChange,
                label = { Text("Precio (S/) *") },
                isError = viewModel.erroresCampo.containsKey("precio"),
                supportingText = { viewModel.erroresCampo["precio"]?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = viewModel.stock,
                onValueChange = viewModel::onStockChange,
                label = { Text("Stock *") },
                isError = viewModel.erroresCampo.containsKey("stock"),
                supportingText = { viewModel.erroresCampo["stock"]?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = viewModel.categoria,
            onValueChange = viewModel::onCategoriaChange,
            label = { Text("Categoría *") },
            isError = viewModel.erroresCampo.containsKey("categoria"),
            supportingText = { viewModel.erroresCampo["categoria"]?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        EstadoDropdown(
            seleccionado = viewModel.estado,
            onSeleccionado = viewModel::onEstadoChange
        )

        Spacer(modifier = Modifier.height(4.dp))

        // -------- Botones --------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = viewModel::publicar,
                enabled = viewModel.uiState != PublicarUiState.Loading,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (viewModel.uiState == PublicarUiState.Loading) "Publicando…" else "Publicar")
            }

            OutlinedButton(
                onClick = viewModel::cancelar,
                enabled = viewModel.uiState != PublicarUiState.Loading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }
        }

        // -------- Estados: carga, éxito y error --------
        when (val state = viewModel.uiState) {
            is PublicarUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Text("Enviando al backend…")
                }
            }

            is PublicarUiState.Success -> {
                Text(
                    text = state.mensaje,
                    color = Color(0xFF166534),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            is PublicarUiState.Error -> {
                Text(
                    text = state.mensaje,
                    color = Color(0xFFB91C1C),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            PublicarUiState.Idle -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EstadoDropdown(seleccionado: String, onSeleccionado: (String) -> Unit) {
    var expandido by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expandido,
        onExpandedChange = { expandido = it }
    ) {
        OutlinedTextField(
            value = seleccionado,
            onValueChange = {},
            readOnly = true,
            label = { Text("Estado") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false }
        ) {
            ESTADOS.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    onClick = {
                        onSeleccionado(opcion)
                        expandido = false
                    }
                )
            }
        }
    }
}
