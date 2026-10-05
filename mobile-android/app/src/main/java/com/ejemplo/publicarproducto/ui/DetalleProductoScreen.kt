package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ejemplo.publicarproducto.model.ProductoResponse

/**
 * HU 07 - Pantalla de detalle de producto.
 * Galería de imagen + precio, stock, categoría, descripción y estado.
 */
@Composable
fun DetalleProductoScreen(
    productoId: Long,
    onVolver: () -> Unit,
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

            viewModel.producto?.estado?.let { estado ->
                EstadoBadge(estado = estado)
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
                viewModel.producto?.let { producto ->
                    DetalleContenido(producto = producto)
                }
            }
        }
    }
}

@Composable
private fun DetalleContenido(producto: ProductoResponse) {
    // Galería de imágenes (hoy: 1 imagen por producto, HU 02)
    val imagen = producto.imagenUrl?.takeIf { it.isNotBlank() }

    if (imagen != null) {
        AsyncImage(
            model = if (imagen.startsWith("http")) {
                imagen
            } else {
                "http://10.0.2.2:8080$imagen"
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
                .background(Color(0xFFE4E7EB)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sin imagen disponible",
                color = Color(0xFF52606D),
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Text(
        text = "Imagen 1 de 1",
        fontSize = 12.sp,
        color = Color(0xFF7B8794),
        modifier = Modifier.fillMaxWidth(),
        fontWeight = FontWeight.Medium
    )

    producto.categoria?.takeIf { it.isNotBlank() }?.let { categoria ->
        Surface(
            color = Color(0xFFE4E7EB),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = categoria.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E4C59),
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
        color = Color(0xFF166534)
    )

    // Ficha técnica
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilaDetalle("Stock", "${producto.stock ?: 0} unidades")
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
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = producto.descripcion.orEmpty(),
            color = Color(0xFF3E4C59),
            lineHeight = 22.sp,
            modifier = Modifier.padding(12.dp)
        )
    }

    if (producto.estado.equals("RETIRADO", ignoreCase = true)) {
        Surface(
            color = Color(0xFFFEE2E2),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Este producto fue retirado del catálogo (baja lógica).",
                color = Color(0xFF991B1B),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun FilaDetalle(etiqueta: String, valor: String) {
    Text(
        text = "$etiqueta: $valor",
        color = Color(0xFF1F2933),
        fontSize = 15.sp
    )
}

@Composable
private fun EstadoBadge(estado: String) {
    val (colorFondo, colorTexto) = when (estado.uppercase()) {
        "DISPONIBLE" -> Color(0xFFDCFCE7) to Color(0xFF166534)
        "AGOTADO" -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        "RETIRADO" -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        else -> Color(0xFFE4E7EB) to Color(0xFF3E4C59)
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
