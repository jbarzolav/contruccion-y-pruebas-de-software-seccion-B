package com.ejemplo.publicarproducto.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ejemplo.publicarproducto.R
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.model.remote.ConfiguracionServidor
import com.ejemplo.publicarproducto.viewmodel.*

/** Catálogo: búsqueda fija y un único scroll para la promoción y los productos. */
@Composable
fun BuscarProductosScreen(
    modifier: Modifier = Modifier,
    viewModel: BuscarProductosViewModel = viewModel(),
    onSeleccionarProducto: (Long) -> Unit = {},
    puedeGuardarFavoritos: Boolean = false
) {
    var menuOrdenAbierto by remember { mutableStateOf(false) }
    var mostrarFavoritos by rememberSaveable { mutableStateOf(false) }
    val teclado = LocalSoftwareKeyboardController.current
    val buscar = {
        viewModel.buscarProductos()
        teclado?.hide()
        Unit
    }
    LaunchedEffect(puedeGuardarFavoritos) {
        viewModel.cargarCatalogo()
        if (puedeGuardarFavoritos) viewModel.cargarFavoritos()
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Una columna en ventanas estrechas; dos en teléfonos y más en tabletas.
        val columnas = if (maxWidth < 340.dp) GridCells.Fixed(1) else GridCells.Adaptive(156.dp)
        Column(Modifier.fillMaxSize()) {
            if (!mostrarFavoritos) {
                OutlinedTextField(
                    value = viewModel.nombre,
                    onValueChange = viewModel::cambiarNombre,
                    placeholder = { Text("Buscar productos", maxLines = 1) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = buscar) { Icon(Icons.Default.Search, "Buscar") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { buscar() }),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (mostrarFavoritos) "Mis favoritos" else "Catálogo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (puedeGuardarFavoritos) {
                    TextButton(onClick = {
                        mostrarFavoritos = !mostrarFavoritos
                        if (mostrarFavoritos) viewModel.cargarFavoritos()
                    }) {
                        Text(if (mostrarFavoritos) "Ver catálogo" else "Favoritos (${viewModel.favoritos.size})")
                    }
                }
                if (!mostrarFavoritos) {
                    Box {
                        TextButton(onClick = { menuOrdenAbierto = true }) { Text("Ordenar") }
                        DropdownMenu(menuOrdenAbierto, { menuOrdenAbierto = false }) {
                            listOf(
                                "precio_asc" to "Precio: menor a mayor",
                                "precio_desc" to "Precio: mayor a menor",
                                "nombre_asc" to "Nombre: A-Z",
                                "nombre_desc" to "Nombre: Z-A"
                            ).forEach { (criterio, etiqueta) ->
                                DropdownMenuItem(
                                    text = { Text(if (viewModel.criterioOrden == criterio) "✓ $etiqueta" else etiqueta) },
                                    onClick = {
                                        menuOrdenAbierto = false
                                        viewModel.cambiarOrden(criterio)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = columnas,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!mostrarFavoritos && viewModel.nombre.isBlank()) {
                    item(key = "comunidad", span = { GridItemSpan(maxLineSpan) }) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Tecnología para tu día a día", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Compra y vende en tu comunidad", style = MaterialTheme.typography.bodySmall)
                                }
                                Image(painterResource(R.drawable.hero_pulgatec), null,
                                    modifier = Modifier.size(64.dp), contentScale = ContentScale.Fit)
                            }
                        }
                    }
                }
                if (puedeGuardarFavoritos) {
                    viewModel.avisoFavorito?.let { aviso ->
                        item(key = "aviso-favorito", span = { GridItemSpan(maxLineSpan) }) {
                            Text(aviso, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (mostrarFavoritos) {
                    when {
                        viewModel.cargandoFavoritos -> item(span = { GridItemSpan(maxLineSpan) }) { CargandoCatalogo() }
                        viewModel.errorFavoritos != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(viewModel.errorFavoritos.orEmpty(), color = MaterialTheme.colorScheme.error)
                        }
                        viewModel.favoritos.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Aún no has guardado productos como favoritos.")
                        }
                        else -> items(viewModel.favoritos, key = { "fav-${it.id ?: 0L}" }) { producto ->
                            TarjetaProductoListado(producto, onSeleccionarProducto, true, true,
                                viewModel.idQuitandoFavorito == producto.id,
                                viewModel::agregarFavorito, viewModel::quitarFavorito)
                        }
                    }
                } else {
                    when (val estado = viewModel.uiState) {
                        BuscarProductosUiState.Initial -> Unit
                        BuscarProductosUiState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { CargandoCatalogo() }
                        is BuscarProductosUiState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
                        }
                        BuscarProductosUiState.Success -> {
                            item(key = "resultados", span = { GridItemSpan(maxLineSpan) }) {
                                Text(if (viewModel.productos.isEmpty()) "No se encontraron productos disponibles."
                                    else "${viewModel.productos.size} productos", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(viewModel.productos, key = { "producto-${it.id ?: 0L}" }) { producto ->
                                TarjetaProductoListado(producto, onSeleccionarProducto, puedeGuardarFavoritos,
                                    producto.id != null && viewModel.favoritos.any { it.id == producto.id },
                                    viewModel.idGuardandoFavorito == producto.id || viewModel.idQuitandoFavorito == producto.id,
                                    viewModel::agregarFavorito, viewModel::quitarFavorito)
                            }
                            val termino = viewModel.terminoRecomendaciones
                            if (viewModel.nombre.trim().isNotBlank() && termino.isNotBlank()) {
                                item(key = "recomendaciones", span = { GridItemSpan(maxLineSpan) }) {
                                    Text("Recomendaciones para “$termino”", style = MaterialTheme.typography.titleMedium)
                                }
                                when (val recomendaciones = viewModel.uiStateRecomendaciones) {
                                    RecomendacionesUiState.Initial -> Unit
                                    RecomendacionesUiState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) { Text("Buscando recomendaciones...") }
                                    is RecomendacionesUiState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(recomendaciones.mensaje, color = MaterialTheme.colorScheme.error)
                                    }
                                    RecomendacionesUiState.Success -> {
                                        if (viewModel.recomendacionesVisibles.isEmpty()) {
                                            item(span = { GridItemSpan(maxLineSpan) }) { Text("No hay recomendaciones relacionadas con esta búsqueda.") }
                                        }
                                        items(viewModel.recomendacionesVisibles, key = { "rec-${it.id ?: 0L}" }) { producto ->
                                            TarjetaProductoListado(producto, onSeleccionarProducto, puedeGuardarFavoritos,
                                                producto.id != null && viewModel.favoritos.any { it.id == producto.id },
                                                viewModel.idGuardandoFavorito == producto.id || viewModel.idQuitandoFavorito == producto.id,
                                                viewModel::agregarFavorito, viewModel::quitarFavorito)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CargandoCatalogo() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** La tarjeta conserva los mismos eventos de detalle y favoritos. */
@Composable
private fun TarjetaProductoListado(
    producto: ProductoResponse,
    onSeleccionarProducto: (Long) -> Unit,
    puedeGuardarFavoritos: Boolean,
    esFavorito: Boolean,
    guardando: Boolean,
    onGuardarFavorito: (ProductoResponse) -> Unit,
    onQuitarFavorito: (ProductoResponse) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = producto.id != null) { producto.id?.let(onSeleccionarProducto) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                val imagen = ConfiguracionServidor.urlImagen(producto.imagenUrl)
                if (imagen != null) {
                    AsyncImage(model = imagen, contentDescription = producto.nombre,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Text("Sin imagen", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(producto.nombre.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("S/ ${producto.precio ?: "0.00"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (producto.esDestacado) {
                Text("★ Destacado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (!producto.estado.isNullOrBlank()) {
                Text(producto.estado.orEmpty(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (puedeGuardarFavoritos) {
                TextButton(
                    onClick = { if (esFavorito) onQuitarFavorito(producto) else onGuardarFavorito(producto) },
                    enabled = !guardando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (guardando) "Guardando..." else if (esFavorito) "♥ Quitar favorito" else "♡ Favorito",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
