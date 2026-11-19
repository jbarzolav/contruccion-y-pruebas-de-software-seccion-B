package com.ejemplo.publicarproducto.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
import com.ejemplo.publicarproducto.model.remote.*
import com.ejemplo.publicarproducto.viewmodel.PagoGrupoViewModel
import java.math.BigDecimal

@Composable
fun PagoPorVendedor(items: List<ItemCarritoResponse>, recargar: () -> Unit, volver: () -> Unit, cuenta: Long) {
    val vm: PagoGrupoViewModel = viewModel(key="pagar-$cuenta")
    var vendedor by rememberSaveable { mutableStateOf<Long?>(null) }
    var seleccion by rememberSaveable { mutableStateOf(longArrayOf()) }
    var idActual by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { if (vm.pago==null) idActual?.let(vm::refrescar) }
    val pago=vm.pago
    LaunchedEffect(pago?.idPedido) { pago?.let { idActual=it.idPedido } }
    if (pago==null) {
        Text("Selecciona los productos de un vendedor", style=MaterialTheme.typography.titleMedium)
        items.groupBy { it.vendedorId }.forEach { (id, grupo) ->
            Text(id?.let { "Vendedor #$it" } ?: "Vendedor no disponible")
            grupo.forEach { item -> Row(verticalAlignment=Alignment.CenterVertically) {
                val itemId=item.id
                Checkbox(checked=id==vendedor && itemId!=null && itemId in seleccion, enabled=id!=null && itemId!=null && !vm.ocupado,
                    onCheckedChange={ marcado -> if(itemId!=null) {
                        val prev=if(vendedor==id) seleccion.toSet() else emptySet()
                        vendedor=id; seleccion=(if(marcado) prev+itemId else prev-itemId).toLongArray()
                    } })
                Text("${item.nombreProducto} × ${item.cantidad} · S/ ${item.subtotal}")
            } }
        }
        val elegidos=items.filter { it.vendedorId==vendedor && it.id in seleccion.toSet() }
        val total=elegidos.fold(BigDecimal.ZERO) { suma,item -> suma+(item.subtotal?:BigDecimal.ZERO) }
        Button(enabled=elegidos.isNotEmpty() && !vm.ocupado, onClick={ vendedor?.let { id ->
            vm.crear(id,elegidos.mapNotNull { it.id })
        } }) { Text("Ir al checkout · S/ $total") }
    } else {
        Text("Pedido ${pago.codigoPedido}", style=MaterialTheme.typography.titleMedium)
        Text("Vendedor #${pago.vendedorId} · ${pago.moneda} ${pago.total}")
        Text("Estado del pago: ${pago.estadoPago}")
        if (pago.estadoPago=="PENDIENTE") {
            Text("Checkout de prueba · no se realizan cobros reales")
            listOf("APROBADO" to "Aprobar pago", "RECHAZADO" to "Rechazar pago").forEach { (estado,etiqueta) ->
                Button(enabled=!vm.ocupado,onClick={ vm.simular(pago,estado) }) { Text(etiqueta) }
            }
        }
        if (pago.estadoPago in listOf("RECHAZADO","PENDIENTE")) TextButton(enabled=!vm.ocupado,onClick={vm.reintentar(pago.idPedido)}) { Text("Reintentar con este pedido") }
        TextButton(enabled=!vm.ocupado,onClick={vm.refrescar(pago.idPedido)}) { Text("Actualizar estado") }
        TextButton(enabled=!vm.ocupado,onClick={ vm.limpiar(); idActual=null; seleccion=longArrayOf(); recargar(); volver() }) { Text("Volver al carrito") }
    }
    vm.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
}
