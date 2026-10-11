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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

/**
 * HU 06 - Búsqueda de productos por nombre.
 * HU 07/HU 08 - catálogo y navegación al detalle.
 * HU 09 - ordenamiento de resultados por precio y nombre.
 */
@Composable
fun BuscarProductosScreen(
    modifier: Modifier = Modifier,
    viewModel: BuscarProductosViewModel = viewModel(),
    onSeleccionarProducto: (Long) -> Unit = {}
) {
    var menuOrdenAbierto by remember { mutableStateOf(false) }

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

        Column {
            OutlinedButton(
                onClick = { menuOrdenAbierto = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (viewModel.criterioOrden) {
                        "precio_asc" -> "Precio: menor a mayor"
                        "precio_desc" -> "Precio: mayor a menor"
                        "nombre_asc" -> "Nombre: A-Z"
                        "nombre_desc" -> "Nombre: Z-A"
                        else -> "Ordenar por"
                    }
                )
            }

            DropdownMenu(
                expanded = menuOrdenAbierto,
                onDismissRequest = { menuOrdenAbierto = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Precio: menor a mayor") },
                    onClick = {
                        menuOrdenAbierto = false
                        viewModel.cambiarOrden("precio_asc")
                    }
                )

                DropdownMenuItem(
                    text = { Text("Precio: mayor a menor") },
                    onClick = {
                        menuOrdenAbierto = false
                        viewModel.cambiarOrden("precio_desc")
                    }
                )

                DropdownMenuItem(
                    text = { Text("Nombre: A-Z") },
                    onClick = {
                        menuOrdenAbierto = false
                        viewModel.cambiarOrden("nombre_asc")
                    }
                )

                DropdownMenuItem(
                    text = { Text("Nombre: Z-A") },
                    onClick = {
                        menuOrdenAbierto = false
                        viewModel.cambiarOrden("nombre_desc")
                    }
                )
            }
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

                                    // HU 19 - distintivo de publicación destacada
                                    if (producto.esDestacado) {
                                        Text(
                                            text = "★ Destacado",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

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