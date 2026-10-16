package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.ejemplo.publicarproducto.model.DisponibilidadProductoResponse
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.ui.theme.AmbarFondo
import com.ejemplo.publicarproducto.ui.theme.AmbarTexto
import com.ejemplo.publicarproducto.ui.theme.RojoErrorFondo
import com.ejemplo.publicarproducto.ui.theme.RojoTextoOscuro
import com.ejemplo.publicarproducto.ui.theme.VerdeExitoFondo
import com.ejemplo.publicarproducto.ui.theme.VerdePrecio

/**
 * HU 07 - Pantalla de detalle de producto.
 * Galería de imagen + precio, stock, categoría, descripción y estado.
 */
@Composable
fun DetalleProductoScreen(
    productoId: Long,
    onVolver: () -> Unit,
    onIrACarrito: () -> Unit = {},
    puedeComprar: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: DetalleProductoViewModel = viewModel()
) {
    LaunchedEffect(productoId) {
        viewModel.cargarProducto(productoId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onVolver) {
                Text("← Volver")
            }

            viewModel.disponibilidad?.let { disponibilidad ->
                EstadoBadge(
                    estado = if (disponibilidad.disponible) "DISPONIBLE" else "AGOTADO"
                )
            }
        }

        when (val state = viewModel.uiState) {
            DetalleProductoUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            is DetalleProductoUiState.Error -> {
                Text(
                    text = state.mensaje,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )

                Button(
                    onClick = { viewModel.cargarProducto(productoId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reintentar")
                }
            }

            DetalleProductoUiState.Success -> {
                val producto = viewModel.producto
                val disponibilidad = viewModel.disponibilidad

                if (producto != null && disponibilidad != null) {
                    DetalleContenido(
                        producto = producto,
                        disponibilidad = disponibilidad,
                        viewModel = viewModel,
                        onIrACarrito = onIrACarrito,
                        puedeComprar = puedeComprar
                    )
                }
            }
        }
    }
}

@Composable
private fun DetalleContenido(
    producto: ProductoResponse,
    disponibilidad: DisponibilidadProductoResponse,
    viewModel: DetalleProductoViewModel,
    onIrACarrito: () -> Unit = {},
    puedeComprar: Boolean = false
) {
    // Galería de imágenes (hoy: 1 imagen por producto, HU 02)
    val imagen = producto.imagenUrl?.takeIf { it.isNotBlank() }

    if (imagen != null) {
        AsyncImage(
            model = if (imagen.startsWith("http")) {
                imagen
            } else {
                "http://10.0.2.2:9000$imagen"
            },
            contentDescription = "Imagen de ${producto.nombre.orEmpty()}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sin imagen disponible",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Text(
        text = "Imagen 1 de 1",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        fontWeight = FontWeight.Medium
    )

    producto.categoria?.takeIf { it.isNotBlank() }?.let { categoria ->
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = categoria.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }

    Text(
        text = producto.nombre.orEmpty(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Text(
        text = "S/ ${producto.precio ?: "0.00"}",
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        color = VerdePrecio
    )

    // Ficha técnica
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilaDetalle(
                "Disponibilidad",
                if (disponibilidad.disponible) "Disponible" else "Agotado"
            )
            FilaDetalle("Stock", "${disponibilidad.stock} unidades")
            FilaDetalle("Estado", disponibilidad.estado)
            FilaDetalle("Categoría", producto.categoria.orEmpty().ifBlank { "Sin categoría" })
            FilaDetalle("Vendedor", "#${producto.vendedorId ?: "-"}")
            FilaDetalle("ID del producto", "#${producto.id ?: "-"}")
        }
    }

    Text(
        text = "Descripción",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = producto.descripcion.orEmpty(),
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
            modifier = Modifier.padding(12.dp)
        )
    }

    if (!disponibilidad.disponible) {
        Surface(
            color = RojoErrorFondo,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Este producto no se encuentra disponible para compra.",
                color = RojoTextoOscuro,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // HU 23 - El detalle es visible para todos; comprar requiere permiso.
    if (puedeComprar) {
    // HU 11 - selector numérico de cantidad (- / +)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Cantidad",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )

        Button(
            onClick = { viewModel.cambiarCantidad(-1) },
            enabled = viewModel.cantidad > 1,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text("−")
        }

        Text(
            text = "${viewModel.cantidad}",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Button(
            onClick = { viewModel.cambiarCantidad(1) },
            enabled = viewModel.cantidad < (disponibilidad.stock ?: 0),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text("+")
        }

        Text(
            text = "Stock: ${disponibilidad.stock ?: 0}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Button(
        onClick = { viewModel.agregarAlCarrito() },
        enabled = disponibilidad.disponible && !viewModel.agregandoCarrito,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (viewModel.agregandoCarrito) "Agregando..." else "Agregar al carrito")
    }

    // estado visual del carrito tras enviar la petición (HU 11)
    viewModel.mensajeCarrito?.let { mensaje ->
        Surface(
            color = VerdeExitoFondo,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = mensaje,
                color = VerdePrecio,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(12.dp)
            )
        }

        // HU 11 - Atajo: ir al carrito sin volver al catálogo
        Button(
            onClick = onIrACarrito,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ir a carrito")
        }
    }

    viewModel.errorCarrito?.let { error ->
        Surface(
            color = RojoErrorFondo,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = error,
                color = RojoTextoOscuro,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
    }
}

@Composable
private fun FilaDetalle(etiqueta: String, valor: String) {
    Text(
        text = "$etiqueta: $valor",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp
    )
}

@Composable
private fun EstadoBadge(estado: String) {
    val (colorFondo, colorTexto) = when (estado.uppercase()) {
        "DISPONIBLE" -> VerdeExitoFondo to VerdePrecio
        "AGOTADO" -> AmbarFondo to AmbarTexto
        "RETIRADO" -> RojoErrorFondo to RojoTextoOscuro
        else -> MaterialTheme.colorScheme.outlineVariant to MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = colorFondo,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = estado,
            color = colorTexto,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
