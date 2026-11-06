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
    onVerDetalle: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MisPedidosViewModel = viewModel()
) {
    var estado by remember { mutableStateOf("") }
    var desde by remember { mutableStateOf("") }
    var hasta by remember { mutableStateOf("") }
    var montoMin by remember { mutableStateOf("") }
    var montoMax by remember { mutableStateOf("") }
    var estadoAplicado by remember { mutableStateOf("") }
    var desdeAplicado by remember { mutableStateOf("") }
    var hastaAplicado by remember { mutableStateOf("") }
    var montoMinAplicado by remember { mutableStateOf("") }
    var montoMaxAplicado by remember { mutableStateOf("") }
    var errorFiltro by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (viewModel.pedidos.isEmpty() && !viewModel.cargando) viewModel.cargar()
        while (true) {
            kotlinx.coroutines.delay(15_000)
            viewModel.cargarNotificaciones()
        }
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
        SelectorFiltro("Estado: todos", estado, listOf(
            "" to "Todos", "REGISTRADO" to "Registrado", "EN_PROCESO" to "En proceso",
            "ENTREGADO" to "Entregado", "CANCELADO" to "Cancelado"
        )) { estado = it }
        OutlinedTextField(desde, { desde = it.take(10) }, label = { Text("Desde (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(hasta, { hasta = it.take(10) }, label = { Text("Hasta (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(montoMin, { montoMin = it }, label = { Text("Monto mínimo (S/)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(montoMax, { montoMax = it }, label = { Text("Monto máximo (S/)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val minimo = montoMin.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
                val maximo = montoMax.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
                errorFiltro = when {
                    !fechaFiltroValida(desde) || !fechaFiltroValida(hasta) -> "Usa fechas reales con formato AAAA-MM-DD."
                    desde.isNotBlank() && hasta.isNotBlank() && desde > hasta -> "La fecha inicial no puede superar la fecha final."
                    montoMin.isNotBlank() && minimo == null || montoMax.isNotBlank() && maximo == null -> "Ingresa montos válidos."
                    (minimo != null && minimo.signum() < 0) || (maximo != null && maximo.signum() < 0) -> "Los montos no pueden ser negativos."
                    minimo != null && maximo != null && minimo > maximo -> "El monto mínimo no puede superar al máximo."
                    else -> null
                }
                if (errorFiltro == null) {
                    estadoAplicado = estado
                    desdeAplicado = desde
                    hastaAplicado = hasta
                    montoMinAplicado = montoMin
                    montoMaxAplicado = montoMax
                }
            }) { Text("Filtrar") }
            TextButton(onClick = {
                estado = ""; desde = ""; hasta = ""; montoMin = ""; montoMax = ""
                estadoAplicado = ""; desdeAplicado = ""; hastaAplicado = ""; montoMinAplicado = ""; montoMaxAplicado = ""
                errorFiltro = null
            }) { Text("Limpiar") }
        }
        errorFiltro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val minimoAplicado = montoMinAplicado.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        val maximoAplicado = montoMaxAplicado.takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        val pedidosFiltrados = viewModel.pedidos.filter { pedido ->
            val fecha = pedido.fecha?.take(10)
            (estadoAplicado.isBlank() || pedido.estado.equals(estadoAplicado, ignoreCase = true)) &&
                (desdeAplicado.isBlank() || (fecha != null && fecha >= desdeAplicado)) &&
                (hastaAplicado.isBlank() || (fecha != null && fecha <= hastaAplicado)) &&
                (minimoAplicado == null || pedido.total?.let { it.compareTo(minimoAplicado) >= 0 } == true) &&
                (maximoAplicado == null || pedido.total?.let { it.compareTo(maximoAplicado) <= 0 } == true)
        }
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
