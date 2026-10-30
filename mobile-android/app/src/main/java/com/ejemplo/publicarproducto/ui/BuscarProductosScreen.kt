package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.R

/**
 * HU 06 - Búsqueda de productos por nombre.
 * HU 07/HU 08 - catálogo y navegación al detalle.
 * HU 09 - ordenamiento de resultados por precio y nombre.
 */
@Composable
fun BuscarProductosScreen(
    modifier: Modifier = Modifier,
    viewModel: BuscarProductosViewModel = viewModel(),
    onSeleccionarProducto: (Long) -> Unit = {},
    puedeGuardarFavoritos: Boolean = false
) {
    var menuOrdenAbierto by remember { mutableStateOf(false) }
    var mostrarFavoritos by remember { mutableStateOf(false) }

    LaunchedEffect(puedeGuardarFavoritos) {
        viewModel.cargarCatalogo()
        if (puedeGuardarFavoritos) viewModel.cargarFavoritos()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PULGATEC / COMUNIDAD TECNOLÓGICA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Compra entre la comunidad.\nVende sin complicarte.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Productos que siguen circulando entre estudiantes y profesionales Tecsup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Image(
                    painter = painterResource(R.drawable.hero_pulgatec),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(148.dp)
                )
            }
        }

        if (puedeGuardarFavoritos) {
            OutlinedButton(
                onClick = {
                    mostrarFavoritos = !mostrarFavoritos
                    if (mostrarFavoritos) viewModel.cargarFavoritos()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (mostrarFavoritos) "Volver al catálogo" else "Mis favoritos (${viewModel.favoritos.size})")
            }
            viewModel.avisoFavorito?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }

        if (mostrarFavoritos) {
            when {
                viewModel.cargandoFavoritos -> CircularProgressIndicator()
                viewModel.errorFavoritos != null -> Text(
                    viewModel.errorFavoritos.orEmpty(), color = MaterialTheme.colorScheme.error
                )
                viewModel.favoritos.isEmpty() -> Text("Aún no has guardado productos como favoritos.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(viewModel.favoritos, key = { "fav-${it.id ?: 0L}" }) { producto ->
                        TarjetaProductoListado(
                            producto = producto,
                            onSeleccionarProducto = onSeleccionarProducto,
                            puedeGuardarFavoritos = true,
                            esFavorito = true,
                            guardando = viewModel.idQuitandoFavorito == producto.id,
                            onGuardarFavorito = viewModel::agregarFavorito,
                            onQuitarFavorito = viewModel::quitarFavorito
                        )
                    }
                }
            }
        }

        if (!mostrarFavoritos) {

        OutlinedTextField(
            value = viewModel.nombre,
            onValueChange = viewModel::cambiarNombre,
            label = { Text("Buscar celulares, accesorios, tecnología...") },
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

        Text("Explora productos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Selecciona un producto para ver su detalle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (val state = viewModel.uiState) {
            BuscarProductosUiState.Initial -> Unit

            BuscarProductosUiState.Loading -> {
                CircularProgressIndicator()
            }

            BuscarProductosUiState.Success -> {
                // HU 20 - la sección usa el término realmente consultado y
                // descarta las recomendaciones que ya están en los resultados
                val terminoBuscado = viewModel.terminoRecomendaciones
                val idsResultados =
                    viewModel.productos.mapNotNull { it.id }.toSet()
                val recomendacionesVisibles = viewModel.recomendaciones.filter {
                    it.id != null && it.id !in idsResultados
                }
                val hayTermino = viewModel.nombre.trim().isNotBlank()

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (viewModel.productos.isEmpty()) {
                        item(key = "sin-resultados") {
                            Text("No se encontraron productos disponibles.")
                        }
                    } else {
                        item(key = "ayuda-busqueda") {
                            Text(
                                text = "Toca un producto para ver su detalle.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        items(
                            items = viewModel.productos,
                            key = { it.id ?: 0L }
                        ) { producto ->
                            TarjetaProductoListado(
                                producto = producto,
                                onSeleccionarProducto = onSeleccionarProducto,
                                puedeGuardarFavoritos = puedeGuardarFavoritos,
                                esFavorito = producto.id != null && viewModel.favoritos.any { it.id == producto.id },
                                guardando = viewModel.idGuardandoFavorito == producto.id || viewModel.idQuitandoFavorito == producto.id,
                                onGuardarFavorito = viewModel::agregarFavorito,
                                onQuitarFavorito = viewModel::quitarFavorito
                            )
                        }
                    }

                    // ===== HU 20 - recomendaciones de la búsqueda actual =====
                    if (hayTermino && terminoBuscado.isNotBlank()) {
                        item(key = "recomendaciones-titulo") {
                            Text(
                                text = "Recomendaciones para “$terminoBuscado”",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        when (val recState = viewModel.uiStateRecomendaciones) {
                            RecomendacionesUiState.Initial -> Unit

                            RecomendacionesUiState.Loading -> {
                                item(key = "recomendaciones-cargando") {
                                    Text("Buscando recomendaciones...")
                                }
                            }

                            is RecomendacionesUiState.Error -> {
                                item(key = "recomendaciones-error") {
                                    Text(
                                        text = recState.mensaje,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            RecomendacionesUiState.Success -> {
                                if (recomendacionesVisibles.isEmpty()) {
                                    item(key = "recomendaciones-vacio") {
                                        Text(
                                            "No hay recomendaciones relacionadas " +
                                                "con esta búsqueda."
                                        )
                                    }
                                } else {
                                    items(
                                        items = recomendacionesVisibles,
                                        key = { "rec-${it.id ?: 0L}" }
                                    ) { producto ->
                                        TarjetaProductoListado(
                                            producto = producto,
                                            onSeleccionarProducto = onSeleccionarProducto,
                                            puedeGuardarFavoritos = puedeGuardarFavoritos,
                                            esFavorito = producto.id != null && viewModel.favoritos.any { it.id == producto.id },
                                            guardando = viewModel.idGuardandoFavorito == producto.id || viewModel.idQuitandoFavorito == producto.id,
                                            onGuardarFavorito = viewModel::agregarFavorito,
                                            onQuitarFavorito = viewModel::quitarFavorito
                                        )
                                    }
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
}

/**
 * HU 07/HU 08 + HU 20 - tarjeta compartida del listado: los resultados
 * de búsqueda y las recomendaciones usan exactamente el mismo diseño y
 * navegan al detalle con el mismo mecanismo (onSeleccionarProducto),
 * sin crear una segunda pantalla de detalle.
 */
@Composable
private fun TarjetaProductoListado(
    producto: ProductoResponse,
    onSeleccionarProducto: (Long) -> Unit,
    puedeGuardarFavoritos: Boolean,
    esFavorito: Boolean,
    guardando: Boolean,
    onGuardarFavorito: (ProductoResponse) -> Unit,
    onQuitarFavorito: ((ProductoResponse) -> Unit)? = null
) {
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
                    model = "http://10.0.2.2:9000${producto.imagenUrl}",
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

            // HU 20 - el DTO de recomendaciones no trae estado: solo se
            // muestra la disponibilidad cuando el producto la informa
            if (!producto.estado.isNullOrBlank()) {
                Text("Disponibilidad: ${producto.estado}")
            }

            if (puedeGuardarFavoritos) {
                OutlinedButton(
                    onClick = { onGuardarFavorito(producto) },
                    enabled = !esFavorito && !guardando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (guardando) "Guardando..." else if (esFavorito) "♥ Guardado" else "♡ Favorito")
                }
                if (esFavorito && onQuitarFavorito != null) {
                    OutlinedButton(
                        onClick = { onQuitarFavorito(producto) },
                        enabled = !guardando,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (guardando) "Quitando..." else "Quitar de favoritos")
                    }
                }
            }
        }
    }
}
