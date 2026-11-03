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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
                        Text("Pedido #${pedido.idPedido ?: "-"}", fontWeight = FontWeight.Bold)
                        Text("Fecha: ${pedido.fecha?.replace('T', ' ') ?: "-"}")
                        Text("Estado: ${pedido.estado ?: "REGISTRADO"}")
                        Text("Última actualización: ${pedido.fechaActualizacion?.replace('T', ' ') ?: pedido.fecha ?: "-"}")
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
                Button(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver a mis pedidos") }
            }
        }
    }
}
