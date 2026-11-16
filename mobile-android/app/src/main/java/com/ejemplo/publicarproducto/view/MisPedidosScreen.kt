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
    onVerDetalle: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MisPedidosViewModel = viewModel()
) {
    // R6 - Antes esta pantalla repetía cada 15 s una llamada a
    // `cargarNotificaciones()`, que ya era un no-op: los avisos se observan
    // de forma reactiva desde la copia local de Room (`observarAvisos`), así
    // que el bucle solo consumía recursos sin aportar nada. Se elimina y el
    // aviso sigue llegando solo cuando cambia la copia local.
    LaunchedEffect(Unit) {
        if (viewModel.pedidos.isEmpty() && !viewModel.cargando) viewModel.cargar()
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text("Mis pedidos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        // Mejora técnica Offline First: modo sin conexión, pendientes y avisos.
        IndicadorOffline(
            sinConexion = viewModel.sinConexion,
            pendientes = viewModel.pendientesSincronizacion,
            aviso = viewModel.avisoSincronizacion,
            rechazo = viewModel.rechazoSincronizacion
        )
        viewModel.notificaciones.filter { it.tipo == "CAMBIO_ESTADO_PEDIDO" }.forEach { aviso ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(resumenNotificacionVisible(aviso), fontWeight = FontWeight.SemiBold)
                    Text(fechaHoraPedidoVisible(aviso.fecha))
                    aviso.idPedido?.let { id -> TextButton(onClick = { onVerDetalle(id) }) { Text("Ver pedido") } }
                }
            }
        }
        SelectorFiltro("Estado: todos", viewModel.filtroEstado, listOf(
            "" to "Todos", "REGISTRADO" to "Registrado", "EN_PROCESO" to "En proceso",
            "ENTREGADO" to "Entregado", "CANCELADO" to "Cancelado"
        )) { viewModel.filtroEstado = it }
        OutlinedTextField(viewModel.filtroDesde, { viewModel.filtroDesde = it.take(10) }, label = { Text("Desde (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(viewModel.filtroHasta, { viewModel.filtroHasta = it.take(10) }, label = { Text("Hasta (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(viewModel.filtroMontoMin, { viewModel.filtroMontoMin = it }, label = { Text("Monto mínimo (S/)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(viewModel.filtroMontoMax, { viewModel.filtroMontoMax = it }, label = { Text("Monto máximo (S/)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::aplicarFiltros) { Text("Filtrar") }
            TextButton(onClick = viewModel::limpiarFiltros) { Text("Limpiar") }
        }
        viewModel.errorFiltro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // Los filtros se aplican en el ViewModel con FiltrosPedidos.
        val pedidosFiltrados = viewModel.pedidosFiltrados
        viewModel.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        when {
            viewModel.cargando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            viewModel.error != null -> {
                Text(viewModel.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::cargar, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
            viewModel.pedidos.isEmpty() -> Text("No tienes pedidos registrados.")
            pedidosFiltrados.isEmpty() -> Text("No hay compras que coincidan con los filtros.")
            else -> pedidosFiltrados.forEach { pedido -> PedidoCard(pedido, viewModel, onVerDetalle) }
        }
    }
}

@Composable
private fun PedidoCard(
    pedido: com.ejemplo.publicarproducto.model.PedidoResponse,
    viewModel: MisPedidosViewModel,
    onVerDetalle: (Long) -> Unit
) {
    var detalleVisible by remember(pedido.idPedido) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("N.º de compra: ${pedido.idPedido ?: "-"}", fontWeight = FontWeight.Bold)
            Text("Fecha: ${fechaHoraPedidoVisible(pedido.fecha)}")
            Text("Estado: ${estadoPedidoVisible(pedido.estado)}")
            Text("Última actualización: ${fechaHoraPedidoVisible(pedido.fechaActualizacion ?: pedido.fecha)}")
            Text("Total: S/ ${pedido.total?.setScale(2)?.toPlainString() ?: "0.00"}")
            TextButton(
                enabled = viewModel.actualizandoEstadoId == null,
                onClick = { pedido.idPedido?.let(viewModel::actualizarEstado) }
            ) {
                Text(if (viewModel.actualizandoEstadoId == pedido.idPedido) "Actualizando..." else "Actualizar estado")
            }
            TextButton(onClick = { detalleVisible = !detalleVisible }) { Text(if (detalleVisible) "Ocultar detalle" else "Ver detalle") }
            // HU 50 - Pantalla dedicada con productos, precios, subtotales y total.
            TextButton(onClick = { pedido.idPedido?.let(onVerDetalle) }) { Text("Detalle del pedido") }
            // HU 59.7 - Cancelar la compra mientras el backend la permita
            // (solo REGISTRADO). El envío se confirma antes y el botón queda
            // bloqueado mientras `cancelandoId` esté en vuelo.
            if (pedido.estado == "REGISTRADO") {
                var confirmandoCancelacion by remember(pedido.idPedido) { mutableStateOf(false) }
                Button(
                    onClick = { confirmandoCancelacion = true },
                    enabled = viewModel.cancelandoId == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (viewModel.cancelandoId == pedido.idPedido) "Cancelando..." else "Cancelar pedido")
                }
                if (confirmandoCancelacion) {
                    AlertDialog(
                        onDismissRequest = { confirmandoCancelacion = false },
                        title = { Text("Cancelar pedido") },
                        text = { Text("¿Seguro que quieres cancelar la compra N.º ${pedido.idPedido}? Esta acción no se puede deshacer.") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmandoCancelacion = false
                                pedido.idPedido?.let(viewModel::cancelarPedido)
                            }) { Text("Sí, cancelar") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmandoCancelacion = false }) { Text("No, volver") }
                        }
                    )
                }
            }
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
