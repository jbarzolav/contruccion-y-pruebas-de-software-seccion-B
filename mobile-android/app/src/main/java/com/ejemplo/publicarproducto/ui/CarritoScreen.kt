package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ejemplo.publicarproducto.model.ItemCarritoResponse

/**
 * HU 12 - Pantalla del carrito: ítems con controles [-] / [+],
 * subtotales dinámicos y total. El backend recalcula cada subtotal.
 */
@Composable
fun CarritoScreen(
    modifier: Modifier = Modifier,
    viewModel: CarritoViewModel = viewModel()
) {
    // cada vez que se entra a la pestaña se recarga el carrito
    LaunchedEffect(Unit) {
        viewModel.cargarCarrito()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Mi carrito",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        viewModel.mensajeError?.let { error ->
            Surface(
                color = Color(0xFFFEE2E2),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    color = Color(0xFF991B1B),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        when (val state = viewModel.uiState) {
            CarritoUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            is CarritoUiState.Error -> {
                Text(
                    text = state.mensaje,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )

                Button(
                    onClick = { viewModel.cargarCarrito() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reintentar")
                }
            }

            CarritoUiState.Success -> {
                if (viewModel.items.isEmpty()) {
                    Text(
                        text = "Tu carrito está vacío.",
                        color = Color(0xFF616E7C)
                    )
                } else {
                    viewModel.items.forEach { item ->
                        ItemCarritoFila(item = item, viewModel = viewModel)
                    }

                    val total = viewModel.totalCarrito.setScale(2).toPlainString()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "S/ $total",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color(0xFF166534)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCarritoFila(
    item: ItemCarritoResponse,
    viewModel: CarritoViewModel
) {
    val actualizando = viewModel.actualizandoId == item.id
    val cantidad = item.cantidad ?: 0
    var confirmarEliminacion by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // HU 13 - Eliminar producto con confirmación
            TextButton(
                onClick = { confirmarEliminacion = true },
                enabled = viewModel.actualizandoId == null
            ) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }

            Text(
                text = item.nombreProducto.orEmpty(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Text(
                text = "Precio unitario: S/ ${item.precioUnitario ?: "0.00"}",
                fontSize = 13.sp,
                color = Color(0xFF616E7C)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // HU 12 - controles de incremento / decremento
                Button(
                    onClick = { viewModel.cambiarCantidad(item, -1) },
                    enabled = cantidad > 1 && !actualizando,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("−")
                }

                Text(
                    text = "$cantidad",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                Button(
                    onClick = { viewModel.cambiarCantidad(item, 1) },
                    enabled = !actualizando,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("+")
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Subtotal: S/ ${item.subtotal ?: "0.00"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    if (confirmarEliminacion) {
        AlertDialog(
            onDismissRequest = { confirmarEliminacion = false },
            title = { Text("Eliminar producto") },
            text = {
                Text("¿Deseas eliminar ${item.nombreProducto.orEmpty()} del carrito?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarEliminacion = false
                        viewModel.eliminarProducto(item)
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarEliminacion = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

}
