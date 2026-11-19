package com.ejemplo.publicarproducto.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ejemplo.publicarproducto.viewmodel.EntregaViewModel

@Composable
fun CoordinarEntrega(id:Long, vendedor:Boolean) {
    val vm:EntregaViewModel=viewModel(key="entrega-$vendedor-$id")
    LaunchedEffect(id) { vm.cargar(id) }
    var abierto by remember(id) { mutableStateOf(false) }
    val pago=vm.resumen
    if(pago!=null) {
        Text("Pedido ${pago.codigoPedido} · Pago: ${pago.estadoPago}",style=MaterialTheme.typography.bodySmall)
        pago.estadoReembolso?.let { Text("Reembolso: $it · ${pago.moneda} ${pago.total}") }
        if(!vendedor && pago.estadoPago=="PAGADO" && pago.estadoEntrega!="ENTREGADO") {
            var confirmar by remember(id) { mutableStateOf(false) }
            if(pago.estadoReembolso=="EN_TRAMITE") {
                Text("Simulador de devolución")
                TextButton(enabled=!vm.ocupado,onClick={vm.devolver(pago,true)}) { Text("Simular aprobación") }
                TextButton(enabled=!vm.ocupado,onClick={vm.devolver(pago,false)}) { Text("Simular fallo") }
            } else TextButton(enabled=!vm.ocupado,onClick={confirmar=true}) { Text("Solicitar reembolso") }
            if(confirmar) AlertDialog(onDismissRequest={confirmar=false}, title={Text("Solicitar devolución total")},
                text={Text("Se solicitará el reembolso de ${pago.moneda} ${pago.total} para este pedido.")},
                confirmButton={TextButton(onClick={confirmar=false; vm.solicitar(pago)}) { Text("Confirmar solicitud") }},
                dismissButton={TextButton(onClick={confirmar=false}) { Text("Volver") }})
        }
        if(pago.estadoPago=="PAGADO") TextButton(onClick={abierto=true; vm.leer(id)}) { Text("Coordinar entrega") }
    }
    if(!abierto && vm.error!=null) TextButton(onClick={vm.cargar(id)}) { Text("Reintentar consulta del pago") }
    if(abierto) AlertDialog(onDismissRequest={abierto=false},title={Text("Coordinar entrega en Tecsup")},text={
        Column {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=220.dp)) { items(vm.mensajes,key={it.id}) { m ->
                Text("Usuario #${m.autorId}: ${m.texto}",modifier=Modifier.padding(vertical=4.dp))
            } }
            TextButton(enabled=!vm.ocupado,onClick={vm.leer(id)}) { Text("Actualizar mensajes") }
            OutlinedTextField(vm.borrador,{if(it.length<=2000) vm.borrador=it},label={Text("Punto de encuentro, día y hora")},enabled=!vm.ocupado)
            vm.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton(enabled=!vm.ocupado && vm.borrador.isNotBlank(),onClick={vm.enviar(id)}) { Text("Enviar") }},
        dismissButton={TextButton(onClick={abierto=false}) { Text("Cerrar") }})
}
