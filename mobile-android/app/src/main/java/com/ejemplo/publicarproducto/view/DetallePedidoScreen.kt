package com.ejemplo.publicarproducto.view

import com.ejemplo.publicarproducto.domain.*
import com.ejemplo.publicarproducto.view.components.*
import com.ejemplo.publicarproducto.viewmodel.*
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * HU 50 - Detalle del pedido: productos, cantidades, precios, subtotales,
 * estado y total. Permite volver al historial de compras.
 */
@Composable
fun DetallePedidoScreen(
    idPedido: Long,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetallePedidoViewModel = viewModel()
) {
    LaunchedEffect(idPedido) { viewModel.cargar(idPedido) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text("Detalle del pedido", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        // Mejora técnica Offline First: modo sin conexión, pendientes y avisos.
        IndicadorOffline(
            sinConexion = viewModel.sinConexion,
            pendientes = viewModel.pendientesSincronizacion,
            aviso = viewModel.avisoSincronizacion,
            rechazo = viewModel.rechazoSincronizacion
        )

        when {
            viewModel.cargando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            viewModel.error != null -> {
                Text(viewModel.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.cargar(idPedido) }, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
            viewModel.pedido == null -> Text("No hay información del pedido.")
            else -> {
                val pedido = viewModel.pedido!!
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("N.º de compra: ${pedido.idPedido ?: "-"}", fontWeight = FontWeight.Bold)
                        Text("Fecha: ${fechaHoraPedidoVisible(pedido.fecha)}")
                        Text("Estado: ${estadoPedidoVisible(pedido.estado)}")
                        Text("Última actualización: ${fechaHoraPedidoVisible(pedido.fechaActualizacion ?: pedido.fecha)}")
                        pedido.detalles.orEmpty().forEach { detalle ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${detalle.nombreProducto.orEmpty()} × ${detalle.cantidad ?: 0}", fontWeight = FontWeight.SemiBold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Precio: S/ ${detalle.precioUnitario?.setScale(2)?.toPlainString() ?: "0.00"}")
                                    Text("Subtotal: S/ ${detalle.subtotal?.setScale(2)?.toPlainString() ?: "0.00"}")
                                }
                            }
                        }
                        Text(
                            "Total: S/ ${pedido.total?.setScale(2)?.toPlainString() ?: "0.00"}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // HU 59.7 - Cancelar la compra mientras el backend la permita
                // (solo REGISTRADO): confirmación previa, bandera anti-doble
                // envío y mensaje de resultado (éxito o error) visible.
                viewModel.mensajeCancelacion?.let { mensaje ->
                    Text(
                        mensaje,
                        color = if (viewModel.errorCancelacion) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
                if (pedido.estado == "REGISTRADO") {
                    var confirmandoCancelacion by remember(pedido.idPedido) { mutableStateOf(false) }
                    Button(
                        onClick = { confirmandoCancelacion = true },
                        enabled = !viewModel.cancelando,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(if (viewModel.cancelando) "Cancelando..." else "Cancelar pedido")
                    }
                    if (confirmandoCancelacion) {
                        AlertDialog(
                            onDismissRequest = { confirmandoCancelacion = false },
                            title = { Text("Cancelar pedido") },
                            text = { Text("¿Seguro que quieres cancelar la compra N.º ${pedido.idPedido}? Esta acción no se puede deshacer.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmandoCancelacion = false
                                    viewModel.cancelarPedido()
                                }) { Text("Sí, cancelar") }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmandoCancelacion = false }) { Text("No, volver") }
                            }
                        )
                    }
                }
                Button(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver a mis pedidos") }
            }
        }
    }
}
