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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.ui.theme.AmbarFondo
import com.ejemplo.publicarproducto.ui.theme.AmbarTexto
import com.ejemplo.publicarproducto.ui.theme.GrisTarjeta
import com.ejemplo.publicarproducto.ui.theme.GrisTexto
import com.ejemplo.publicarproducto.ui.theme.RojoErrorFondo
import com.ejemplo.publicarproducto.ui.theme.RojoTextoOscuro
import com.ejemplo.publicarproducto.ui.theme.VerdeExitoFondo
import com.ejemplo.publicarproducto.ui.theme.VerdePrecio


/**
 * HU 05 - Lista de productos del vendedor (gestión).
 * HU 19 - botón de destacar (Premium).
 * Rediseño: tarjeta con imagen redondeada, insignias (★ Destacado / estado),
 * precio en verde jerárquico y acciones alineadas en fila.
 * El "Editar" precarga el producto (sin digitar ID).
 */
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

        Text(
            text = "Edita, retira o destaca tus publicaciones",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Todavía no tienes productos publicados. " +
                                    "Toca el botón + para crear el primero.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                                },
                                // HU 19 - gestionar destacado (Premium)
                                onDestacar = { activar ->
                                    producto.id?.let {
                                        viewModel.gestionarDestacado(it, activar)
                                    }
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

/** Insignia compacta de estado (DISPONIBLE / AGOTADO / RETIRADO / INACTIVO). */
@Composable
private fun InsigniaEstado(estado: String) {
    val (fondo, texto) = when (estado.uppercase()) {
        "DISPONIBLE" -> VerdeExitoFondo to VerdePrecio
        "AGOTADO" -> AmbarFondo to AmbarTexto
        "RETIRADO" -> RojoErrorFondo to RojoTextoOscuro
        else -> GrisTarjeta to GrisTexto
    }

    Surface(
        color = fondo,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = estado,
            color = texto,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Insignia ★ Destacado con fondo de marca (antes era texto plano). */
@Composable
private fun InsigniaDestacado() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "★ Destacado",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ProductoItem(
    producto: ProductoResponse,
    onEditar: () -> Unit,
    onVerDetalle: () -> Unit,
    onDestacar: (Boolean) -> Unit = {}
) {
    Card(
        // HU 08 - la tarjeta abre el detalle (HU 07)
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = producto.id != null) { onVerDetalle() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            if (!producto.imagenUrl.isNullOrBlank()) {
                AsyncImage(
                    model = "http://10.0.2.2:9000${producto.imagenUrl}",
                    contentDescription = "Imagen de ${producto.nombre.orEmpty()}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }

            // Insignias: destacado + estado del producto
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (producto.esDestacado) {
                    InsigniaDestacado()
                }

                InsigniaEstado(producto.estado.orEmpty())
            }

            Text(
                text = producto.nombre.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Precio en verde de marca, jerarquía alta
            Text(
                text = "S/ ${producto.precio ?: "0.00"}",
                style = MaterialTheme.typography.titleLarge,
                color = VerdePrecio,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Stock: ${producto.stock ?: 0}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Acciones alineadas en fila
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onVerDetalle,
                    enabled = producto.id != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver detalle")
                }

                Button(
                    onClick = onEditar,
                    enabled = producto.id != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Editar")
                }
            }

            // HU 19 - gestionar el destacado: activar o desactivar (Premium)
            OutlinedButton(
                onClick = { onDestacar(!producto.esDestacado) },
                enabled = producto.id != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (producto.esDestacado) "Quitar destacado" else "Destacar (Premium)")
            }
        }
    }
}
