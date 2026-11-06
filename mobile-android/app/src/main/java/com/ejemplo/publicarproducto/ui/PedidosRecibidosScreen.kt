package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.ejemplo.publicarproducto.model.PedidoVendedorResponse

/**
 * HU 48 - Pedidos recibidos con solo los productos del vendedor.
 * HU 49 - Selector de estado y acción Guardar sobre cada pedido.
 */
@Composable
fun PedidosRecibidosScreen(
    modifier: Modifier = Modifier,
    viewModel: PedidosRecibidosViewModel = viewModel()
) {
    var estado by remember { mutableStateOf("") }
    var desde by remember { mutableStateOf("") }
    var hasta by remember { mutableStateOf("") }
    var productoId by remember { mutableStateOf("") }
    var estadoAplicado by remember { mutableStateOf("") }
    var desdeAplicado by remember { mutableStateOf("") }
    var hastaAplicado by remember { mutableStateOf("") }
    var productoIdAplicado by remember { mutableStateOf("") }
    var errorFiltro by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (viewModel.pedidos.isEmpty() && !viewModel.cargando) viewModel.cargar()
        while (true) {
            kotlinx.coroutines.delay(15_000)
            viewModel.cargarNotificaciones()
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pedidos recibidos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        // Mejora técnica Offline First: modo sin conexión, pendientes y avisos.
        IndicadorOffline(
            sinConexion = viewModel.sinConexion,
            pendientes = viewModel.pendientesSincronizacion,
            aviso = viewModel.avisoSincronizacion,
            rechazo = viewModel.rechazoSincronizacion
        )
        viewModel.notificaciones.filter { it.tipo == "NUEVA_COMPRA" }.forEach { aviso ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(resumenNotificacionVisible(aviso), fontWeight = FontWeight.SemiBold)
                    Text(fechaHoraPedidoVisible(aviso.fecha))
                    aviso.idPedido?.let { id ->
                        TextButton(onClick = { viewModel.enfocarPedido(id) }) { Text("Ver pedido recibido") }
                    }
                }
            }
        }
        SelectorFiltro("Estado: todos", estado, listOf(
            "" to "Todos", "REGISTRADO" to "Registrado", "EN_PROCESO" to "En proceso",
            "ENTREGADO" to "Entregado", "CANCELADO" to "Cancelado"
        )) { estado = it }
        OutlinedTextField(desde, { desde = it.take(10) }, label = { Text("Desde (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(hasta, { hasta = it.take(10) }, label = { Text("Hasta (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        val opcionesProducto = viewModel.pedidos.flatMap { it.productos }
            .distinctBy { it.idProducto }
            .mapNotNull { producto -> producto.idProducto?.let { it.toString() to producto.nombreProducto.orEmpty() } }
        SelectorFiltro("Producto: todos", productoId, listOf("" to "Todos") + opcionesProducto) { productoId = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                errorFiltro = when {
                    !fechaFiltroValida(desde) || !fechaFiltroValida(hasta) -> "Usa fechas reales con formato AAAA-MM-DD."
                    desde.isNotBlank() && hasta.isNotBlank() && desde > hasta -> "La fecha inicial no puede superar la fecha final."
                    else -> null
                }
                if (errorFiltro == null) {
                    estadoAplicado = estado
                    desdeAplicado = desde
                    hastaAplicado = hasta
                    productoIdAplicado = productoId
                }
            }) { Text("Filtrar") }
            TextButton(onClick = {
                estado = ""; desde = ""; hasta = ""; productoId = ""
                estadoAplicado = ""; desdeAplicado = ""; hastaAplicado = ""; productoIdAplicado = ""
                errorFiltro = null
            }) { Text("Limpiar") }
        }
        errorFiltro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val pedidosFiltrados = viewModel.pedidos.filter { pedido ->
            val fecha = pedido.fecha?.take(10)
            (estadoAplicado.isBlank() || pedido.estado.equals(estadoAplicado, ignoreCase = true)) &&
                (desdeAplicado.isBlank() || (fecha != null && fecha >= desdeAplicado)) &&
                (hastaAplicado.isBlank() || (fecha != null && fecha <= hastaAplicado)) &&
                (productoIdAplicado.isBlank() || pedido.productos.any { it.idProducto?.toString() == productoIdAplicado })
        }
        viewModel.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        when {
            viewModel.cargando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            viewModel.error != null -> {
                Text(viewModel.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::cargar, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
            viewModel.pedidos.isEmpty() -> Text("Aún no tienes pedidos que incluyan tus productos.")
            pedidosFiltrados.isEmpty() -> Text("No hay pedidos que coincidan con los filtros.")
            else -> pedidosFiltrados.sortedByDescending { it.idPedido == viewModel.pedidoEnfocadoId }
                .forEach { pedido -> PedidoRecibidoCard(pedido, viewModel) }
        }
    }
}

@Composable
private fun PedidoRecibidoCard(pedido: PedidoVendedorResponse, viewModel: PedidosRecibidosViewModel) {
    var menuAbierto by remember(pedido.idPedido) { mutableStateOf(false) }
    var seleccionado by remember(pedido.idPedido) { mutableStateOf("") }
    val permitidos = pedido.estadosPermitidos.orEmpty()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("N.º de compra: ${pedido.idPedido}", fontWeight = FontWeight.Bold)
            Text("Fecha: ${fechaHoraPedidoVisible(pedido.fecha)}")
            Text("Estado: ${estadoPedidoVisible(pedido.estado)}")
            // Mejora técnica Offline First: el estado propuesto por el usuario
            // se muestra como pendiente hasta que el servidor lo confirme.
            viewModel.estadosPendientes[pedido.idPedido]?.let { pendiente ->
                Text(
                    "Estado pendiente de sincronizar: ${estadoPedidoVisible(pendiente)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text("Última actualización: ${fechaHoraPedidoVisible(pedido.fechaActualizacion ?: pedido.fecha)}")
            pedido.productos.forEach { producto ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${producto.nombreProducto.orEmpty()} × ${producto.cantidad ?: 0}")
                    Text("S/ ${producto.subtotal?.setScale(2)?.toPlainString() ?: "0.00"}")
                }
            }
            Text("Total de tus productos: S/ ${pedido.total.setScale(2).toPlainString()}", fontWeight = FontWeight.SemiBold)

            if (permitidos.isNotEmpty()) {
                Box {
                    OutlinedButton(
                        onClick = { menuAbierto = true },
                        enabled = viewModel.guardandoId == null
                    ) {
                        Text(if (seleccionado.isBlank()) "Seleccionar estado" else estadoPedidoVisible(seleccionado))
                    }
                    DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                        permitidos.forEach { estado ->
                            DropdownMenuItem(
                                text = { Text(estadoPedidoVisible(estado)) },
                                onClick = {
                                    seleccionado = estado
                                    menuAbierto = false
                                }
                            )
                        }
                    }
                }
                Button(
                    onClick = { viewModel.actualizarEstado(pedido.idPedido, seleccionado) },
                    enabled = seleccionado.isNotBlank() && viewModel.guardandoId == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (viewModel.guardandoId == pedido.idPedido) "Guardando..." else "Guardar estado")
                }
            } else {
                Text("Este pedido ya no admite cambios de estado.")
            }
        }
    }
}
