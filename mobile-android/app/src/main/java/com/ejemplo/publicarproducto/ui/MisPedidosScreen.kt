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
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun MisPedidosScreen(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MisPedidosViewModel = viewModel()
) {
    LaunchedEffect(Unit) { if (viewModel.pedidos.isEmpty() && !viewModel.cargando) viewModel.cargar() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text("Mis pedidos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        viewModel.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        when {
            viewModel.cargando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            viewModel.error != null -> {
                Text(viewModel.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::cargar, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
            viewModel.pedidos.isEmpty() -> Text("No tienes pedidos registrados.")
            else -> viewModel.pedidos.forEach { pedido -> PedidoCard(pedido, viewModel) }
        }
    }
}

@Composable
private fun PedidoCard(pedido: com.ejemplo.publicarproducto.model.PedidoResponse, viewModel: MisPedidosViewModel) {
    var detalleVisible by remember(pedido.idPedido) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pedido #${pedido.idPedido ?: "-"}", fontWeight = FontWeight.Bold)
            Text("Fecha: ${pedido.fecha ?: "-"}")
            Text("Estado: Registrado")
            Text("Total: S/ ${pedido.total?.setScale(2)?.toPlainString() ?: "0.00"}")
            TextButton(onClick = { detalleVisible = !detalleVisible }) { Text(if (detalleVisible) "Ocultar detalle" else "Ver detalle") }
            if (detalleVisible) {
                pedido.detalles.orEmpty().forEach { Text("${it.nombreProducto.orEmpty()} × ${it.cantidad ?: 0}") }
                pedido.resenas.orEmpty().forEach { resena -> ResenaEditable(pedido.idPedido ?: return@forEach, resena, viewModel) }
            }
        }
    }
}

@Composable
private fun ResenaEditable(
    idPedido: Long,
    resena: com.ejemplo.publicarproducto.model.ResenaResponse,
    viewModel: MisPedidosViewModel
) {
    var editando by remember(resena.id, resena.fechaModificacion) { mutableStateOf(false) }
    var texto by remember(resena.id, resena.fechaModificacion) { mutableStateOf(resena.texto.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Reseña ${resena.destino?.lowercase() ?: ""}", fontWeight = FontWeight.SemiBold)
        if (editando) {
            OutlinedTextField(value = texto, onValueChange = { if (it.length <= 1000) texto = it },
                supportingText = { Text("${texto.length} / 1000") }, enabled = !viewModel.guardando,
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = texto.trim().isNotEmpty() && !viewModel.guardando,
                    onClick = { viewModel.editarResena(idPedido, resena.id ?: return@Button, texto); editando = false }) { Text("Guardar") }
                TextButton(onClick = { editando = false }) { Text("Cancelar") }
            }
        } else {
            Text(resena.texto.orEmpty())
            TextButton(onClick = { editando = true }) { Text("Editar reseña") }
        }
    }
}
