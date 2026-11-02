package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PedidosRecibidosScreen(
    modifier: Modifier = Modifier,
    viewModel: PedidosRecibidosViewModel = viewModel()
) {
    LaunchedEffect(Unit) { if (viewModel.pedidos.isEmpty() && !viewModel.cargando) viewModel.cargar() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pedidos recibidos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        when {
            viewModel.cargando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            viewModel.error != null -> {
                Text(viewModel.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::cargar, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
            viewModel.pedidos.isEmpty() -> Text("Aún no tienes pedidos que incluyan tus productos.")
            else -> viewModel.pedidos.forEach { pedido ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pedido #${pedido.idPedido}", fontWeight = FontWeight.Bold)
                        Text("Fecha: ${pedido.fecha ?: "-"}")
                        Text("Estado: ${pedido.estado}")
                        Text("Última actualización: ${pedido.fechaActualizacion?.replace('T', ' ') ?: pedido.fecha ?: "-"}")
                        pedido.productos.forEach { producto ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${producto.nombreProducto.orEmpty()} × ${producto.cantidad ?: 0}")
                                Text("S/ ${producto.subtotal?.setScale(2)?.toPlainString() ?: "0.00"}")
                            }
                        }
                        Text("Total de tus productos: S/ ${pedido.total.setScale(2).toPlainString()}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
