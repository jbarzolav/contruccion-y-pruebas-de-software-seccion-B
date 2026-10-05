package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

/**
 * HU 06 - Búsqueda de productos por nombre.
 * HU 07/HU 08 - al abrir la pestaña se carga el catálogo y las tarjetas
 * son clickeables: llevan al detalle del producto seleccionado.
 */
@Composable
fun BuscarProductosScreen(
    modifier: Modifier = Modifier,
    viewModel: BuscarProductosViewModel = viewModel(),
    onSeleccionarProducto: (Long) -> Unit = {}
) {
    // Catálogo al entrar (base para seleccionar un producto - HU 08)
    LaunchedEffect(Unit) {
        viewModel.cargarCatalogo()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Buscar productos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = viewModel.nombre,
            onValueChange = viewModel::cambiarNombre,
            label = { Text("Nombre del producto") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = viewModel::buscarProductos,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Buscar")
        }

        when (val state = viewModel.uiState) {
            BuscarProductosUiState.Initial -> Unit

            BuscarProductosUiState.Loading -> {
                CircularProgressIndicator()
            }

            BuscarProductosUiState.Success -> {
                if (viewModel.productos.isEmpty()) {
                    Text("No se encontraron productos disponibles.")
                } else {
                    Text(
                        text = "Toca un producto para ver su detalle.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = viewModel.productos,
                            key = { it.id ?: 0L }
                        ) { producto ->
                            Card(
                                // HU 08 - la tarjeta abre el detalle (HU 07)
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = producto.id != null) {
                                        producto.id?.let(onSeleccionarProducto)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!producto.imagenUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = "http://10.0.2.2:8080${producto.imagenUrl}",
                                            contentDescription = "Imagen de ${producto.nombre.orEmpty()}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                        )
                                    }

                                    Text(
                                        text = producto.nombre.orEmpty(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text("Precio: S/ ${producto.precio ?: "0.00"}")
                                    Text("Disponibilidad: ${producto.estado.orEmpty()}")
                                }
                            }
                        }
                    }
                }
            }

            is BuscarProductosUiState.Error -> {
                Text(
                    text = state.mensaje,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
