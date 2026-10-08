package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * HU 15 - "Confirmar mi compra": resumen final del carrito (lista de
 * productos, subtotales y total general) con botón "Confirmar compra".
 * Al tener éxito se muestra el comprobante con el ID del pedido y el
 * carrito queda vacío (lo descuenta y vacía el backend).
 */
@Composable
fun ConfirmarCompraScreen(
    modifier: Modifier = Modifier,
    viewModel: ConfirmarCompraViewModel = viewModel()
) {
    // cada vez que se entra a la pestaña se recarga el resumen
    LaunchedEffect(Unit) {
        if (viewModel.pedido == null) {
            viewModel.cargarResumen()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Confirmar compra",
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

        // Comprobante: pedido registrado con su ID
        val pedidoActual = viewModel.pedido
        if (pedidoActual != null) {
            ComprobantePedido(viewModel = viewModel)
            return@Column
        }

        when (val state = viewModel.uiState) {
            ConfirmarCompraUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            is ConfirmarCompraUiState.Error -> {
                Text(
                    text = state.mensaje,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )

                Button(
                    onClick = { viewModel.cargarResumen() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reintentar")
                }
            }

            ConfirmarCompraUiState.Success -> {
                if (viewModel.items.isEmpty()) {
                    Text(
                        text = "Tu carrito está vacío: agrega productos antes de confirmar la compra.",
                        color = Color(0xFF616E7C)
                    )
                } else {
                    ResumenCompra(viewModel = viewModel)
                }
            }
        }
    }
}

/** Lista de productos con sus subtotales + total general + botón de confirmación. */
@Composable
private fun ResumenCompra(viewModel: ConfirmarCompraViewModel) {
    var confirmacionPendiente by remember { mutableStateOf(false) }

    viewModel.items.forEach { item ->
        ResumenFila(item = item)
        HorizontalDivider(color = Color(0xFFE4E7EB))
    }

    val total = viewModel.totalCarrito.setScale(2).toPlainString()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Total a pagar",
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

    Button(
        onClick = { confirmacionPendiente = true },
        enabled = !viewModel.confirmando,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (viewModel.confirmando) "Registrando pedido..." else "Confirmar compra")
    }

    // Confirmación previa al POST /api/pedidos
    if (confirmacionPendiente) {
        AlertDialog(
            onDismissRequest = { confirmacionPendiente = false },
            title = { Text("Confirmar compra") },
            text = {
                Text("¿Registrar el pedido por S/ $total? El carrito quedará vacío.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmacionPendiente = false
                        viewModel.confirmarCompra()
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmacionPendiente = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/** Fila del resumen: producto, cantidad × precio y subtotal. */
@Composable
private fun ResumenFila(item: ItemCarritoResponse) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.nombreProducto.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${item.cantidad ?: 0} × S/ ${item.precioUnitario ?: "0.00"}",
                    fontSize = 13.sp,
                    color = Color(0xFF616E7C)
                )
            }

            Text(
                text = "Subtotal: S/ ${item.subtotal ?: "0.00"}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

/** Comprobante del pedido registrado (ID, fecha, detalle y total). */
@Composable
private fun ComprobantePedido(viewModel: ConfirmarCompraViewModel) {
    val pedido = viewModel.pedido ?: return

    Surface(
        color = Color(0xFFDCFCE7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "✅ Pedido registrado",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF166534)
            )

            Text(
                text = "Tu pedido #${pedido.idPedido ?: "-"} fue registrado correctamente.",
                fontWeight = FontWeight.Bold
            )

            pedido.fecha?.let { fecha ->
                Text(
                    text = "Fecha: $fecha",
                    fontSize = 13.sp,
                    color = Color(0xFF616E7C)
                )
            }

            HorizontalDivider(color = Color(0xFF86EFAC))

            pedido.detalles.orEmpty().forEach { detalle ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${detalle.nombreProducto.orEmpty()} × ${detalle.cantidad ?: 0}",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "S/ ${detalle.subtotal ?: "0.00"}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF86EFAC))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total pagado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "S/ ${pedido.total?.setScale(2)?.toPlainString() ?: "0.00"}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF166534)
                )
            }

            Button(
                onClick = { viewModel.reiniciar() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Seguir comprando")
            }
        }
    }
}
