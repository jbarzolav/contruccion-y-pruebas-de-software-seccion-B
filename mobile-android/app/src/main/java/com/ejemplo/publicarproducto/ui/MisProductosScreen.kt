package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ejemplo.publicarproducto.model.ProductoResponse


@Composable
fun MisProductosScreen(
    modifier: Modifier = Modifier,
    viewModel: MisProductosViewModel = viewModel(),
    onEditarProducto: (Long) -> Unit = {},
    onVerDetalle: (Long) -> Unit = {}
) {
    LaunchedEffect(Unit) {
        viewModel.cargarProductos()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Mis productos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        when (val state = viewModel.uiState) {
            MisProductosUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Cargando productos...")
                }
            }

            MisProductosUiState.Success -> {
                if (viewModel.productos.isEmpty()) {
                    Text("Todavía no tienes productos publicados.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = viewModel.productos,
                            key = { it.id ?: 0L }
                        ) { producto ->
                            ProductoItem(
                                producto = producto,
                                onEditar = {
                                    producto.id?.let(onEditarProducto)
                                },
                                // HU 08 - ver detalle del producto propio
                                onVerDetalle = {
                                    producto.id?.let(onVerDetalle)
                                }
                            )
                        }
                    }
                }
            }

            is MisProductosUiState.Error -> {
                Text(
                    text = state.mensaje,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )

                Button(onClick = viewModel::cargarProductos) {
                    Text("Reintentar")
                }
            }
        }
    }
}

@Composable
private fun ProductoItem(
    producto: ProductoResponse,
    onEditar: () -> Unit,
    onVerDetalle: () -> Unit
) {
    Card(
        // HU 08 - la tarjeta abre el detalle (HU 07)
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = producto.id != null) { onVerDetalle() }
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
            Text("Stock: ${producto.stock ?: 0}")
            Text("Estado: ${producto.estado.orEmpty()}")

            OutlinedButton(
                onClick = onVerDetalle,
                enabled = producto.id != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver detalle")
            }

            Button(
                onClick = onEditar,
                enabled = producto.id != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Editar")
            }
        }
    }
}
