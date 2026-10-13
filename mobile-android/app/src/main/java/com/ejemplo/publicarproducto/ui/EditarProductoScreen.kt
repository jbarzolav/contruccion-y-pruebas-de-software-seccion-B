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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ejemplo.publicarproducto.ui.theme.VerdePrecio

/**
 * HU 03 - Editar la información de mis productos.
 * HU 04 - Retirar publicación con diálogo de confirmación (baja lógica).
 * Rediseño: el producto llega precargado desde "Mis productos"
 * (sin digitar el ID) y la pantalla tiene botón "Volver".
 */
@Composable
fun EditarProductoScreen(
    productoId: Long?,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditarProductoViewModel = viewModel()
) {
    LaunchedEffect(productoId) {
        productoId?.let {
            viewModel.onIdChange(it.toString())
            viewModel.cargarProducto()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onVolver) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }

            Text(
                text = "Editar / Retirar producto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (productoId == null) {
            Text(
                text = "Selecciona un producto desde \"Mis productos\" para editarla.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --------------------------------------------------------------
        // Formulario (solo si hay producto cargado)
        // --------------------------------------------------------------
        if (viewModel.producto != null) {

            Text(
                text = if (viewModel.estaRetirado) {
                    "Estado actual: RETIRADO (publicación retirada)"
                } else {
                    "Estado actual: ${viewModel.estado}"
                },
                fontWeight = FontWeight.SemiBold,
                color = if (viewModel.estaRetirado) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = viewModel.nombre,
                onValueChange = viewModel::onNombreChange,
                label = { Text("Nombre *") },
                isError = viewModel.erroresCampo.containsKey("nombre"),
                supportingText = {
                    viewModel.erroresCampo["nombre"]?.let { Text(it) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.descripcion,
                onValueChange = viewModel::onDescripcionChange,
                label = { Text("Descripción *") },
                isError = viewModel.erroresCampo.containsKey("descripcion"),
                supportingText = {
                    viewModel.erroresCampo["descripcion"]?.let { Text(it) }
                },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.precio,
                    onValueChange = viewModel::onPrecioChange,
                    label = { Text("Precio (S/) *") },
                    isError = viewModel.erroresCampo.containsKey("precio"),
                    supportingText = {
                        viewModel.erroresCampo["precio"]?.let { Text(it) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = viewModel.stock,
                    onValueChange = viewModel::onStockChange,
                    label = { Text("Stock *") },
                    isError = viewModel.erroresCampo.containsKey("stock"),
                    supportingText = {
                        viewModel.erroresCampo["stock"]?.let { Text(it) }
                    },
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
                supportingText = {
                    viewModel.erroresCampo["categoria"]?.let { Text(it) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            EstadoDropdown(
                seleccionado = viewModel.estado,
                onSeleccionado = viewModel::onEstadoChange
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::guardar,
                    enabled = viewModel.uiState != EditarUiState.Loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (viewModel.uiState == EditarUiState.Loading) {
                            "Guardando…"
                        } else {
                            "Guardar cambios"
                        }
                    )
                }

                OutlinedButton(
                    onClick = viewModel::limpiar,
                    enabled = viewModel.uiState != EditarUiState.Loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Limpiar")
                }
            }

            // HU 04 - Retirar publicación
            Button(
                onClick = viewModel::pedirConfirmacionRetiro,
                enabled = viewModel.uiState != EditarUiState.Loading && !viewModel.estaRetirado,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (viewModel.estaRetirado) "Ya retirado" else "Retirar publicación"
                )
            }
        }

        // --------------------------------------------------------------
        // Retroalimentación
        // --------------------------------------------------------------
        when (val state = viewModel.uiState) {
            is EditarUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp)
                    )

                    Text("Consultando al backend…")
                }
            }

            is EditarUiState.Success -> {
                Text(
                    text = state.mensaje,
                    color = VerdePrecio,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            is EditarUiState.Error -> {
                Text(
                    text = state.mensaje,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            EditarUiState.Idle -> Unit
        }
    }

    // --------------------------------------------------------------
    // HU 04 - Diálogo de confirmación antes de retirar
    // --------------------------------------------------------------
    if (viewModel.mostrarDialogoRetiro) {
        AlertDialog(
            onDismissRequest = viewModel::cancelarRetiro,
            title = {
                Text("¿Retirar esta publicación?")
            },
            text = {
                Text(
                    "El producto ${viewModel.nombre} (ID ${viewModel.producto?.id}) " +
                            "quedará con estado RETIRADO. No se eliminará de la base de " +
                            "datos: es una baja lógica."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::retirar) {
                    Text(
                        "Sí, retirar",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelarRetiro) {
                    Text("Cancelar")
                }
            }
        )
    }
}
