package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
import com.ejemplo.publicarproducto.ui.theme.RojoErrorFondo
import com.ejemplo.publicarproducto.ui.theme.RojoTextoOscuro
import com.ejemplo.publicarproducto.ui.theme.VerdeExitoFondo
import com.ejemplo.publicarproducto.ui.theme.VerdePrecio

/**
 * HU 15 - "Confirmar mi compra": resumen final del carrito (lista de
 * productos, subtotales y total general) con botón "Confirmar compra".
 * Al tener éxito se muestra el comprobante con el ID del pedido y el
 * carrito queda vacío (lo descuenta y vacía el backend).
 */
@Composable
fun ConfirmarCompraScreen(
    onVolver: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onVolver) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }

            Text(
                text = "Confirmar compra",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        viewModel.mensajeError?.let { error ->
            Surface(
                color = RojoErrorFondo,
                shape = MaterialTheme.shapes.medium,
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

        // Comprobante: pedido registrado con su ID
        val pedidoActual = viewModel.pedido
        if (pedidoActual != null) {
            ComprobantePedido(viewModel = viewModel, onVolver = onVolver)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            color = VerdePrecio
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
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ComprobantePedido(
    viewModel: ConfirmarCompraViewModel,
    onVolver: () -> Unit
) {
    val pedido = viewModel.pedido ?: return

    Surface(
        color = VerdeExitoFondo,
        shape = MaterialTheme.shapes.medium,
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
                color = VerdePrecio
            )

            Text(
                text = "Tu pedido #${pedido.idPedido ?: "-"} fue registrado correctamente.",
                fontWeight = FontWeight.Bold
            )

            pedido.fecha?.let { fecha ->
                Text(
                    text = "Fecha: $fecha",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = VerdePrecio.copy(alpha = 0.25f))

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

            HorizontalDivider(color = VerdePrecio.copy(alpha = 0.25f))

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
                    color = VerdePrecio
                )
            }

            ExperienciaCompra(viewModel)

            Button(
                onClick = {
                    viewModel.reiniciar()
                    onVolver()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Seguir comprando")
            }
        }
    }
}

@Composable
private fun ExperienciaCompra(viewModel: ConfirmarCompraViewModel) {
    val pedido = viewModel.pedido ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Tu experiencia", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(if (pedido.calificacion != null) "Calificación guardada" else "Calificar compra")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..5).forEach { numero ->
                TextButton(enabled = pedido.calificacion == null && !viewModel.guardandoExperiencia,
                    onClick = { viewModel.valorCalificacion = numero }) { Text(if (numero <= viewModel.valorCalificacion) "★" else "☆", fontSize = 24.sp) }
            }
        }
        if (pedido.calificacion == null) Button(enabled = viewModel.valorCalificacion in 1..5 && !viewModel.guardandoExperiencia,
            onClick = { viewModel.calificar(viewModel.valorCalificacion) }) { Text(if (viewModel.guardandoExperiencia) "Guardando..." else "Guardar calificación") }
        val existente = pedido.resenas.orEmpty().firstOrNull { it.destino == viewModel.destinoResena }
        if (existente != null) Text("Reseña ${viewModel.destinoResena.lowercase()}: ${existente.texto}")
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !viewModel.guardandoExperiencia, onClick = { viewModel.destinoResena = if (viewModel.destinoResena == "PRODUCTO") "VENDEDOR" else "PRODUCTO" }) { Text(viewModel.destinoResena) }
            }
            OutlinedTextField(value = viewModel.textoResena, onValueChange = { if (it.length <= 1000) viewModel.textoResena = it },
                label = { Text("Reseña") }, supportingText = { Text("${viewModel.textoResena.length} / 1000") },
                enabled = !viewModel.guardandoExperiencia, modifier = Modifier.fillMaxWidth())
            Button(enabled = viewModel.textoResena.trim().isNotEmpty() && !viewModel.guardandoExperiencia,
                onClick = { viewModel.resenar() }) { Text(if (viewModel.guardandoExperiencia) "Publicando..." else "Publicar reseña") }
        }
        viewModel.mensajeExperiencia?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
