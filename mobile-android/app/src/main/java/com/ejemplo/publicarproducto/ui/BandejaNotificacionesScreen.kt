package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ejemplo.publicarproducto.model.NotificacionResponse

// El estado y los trabajos de red pertenecen a esta pantalla y se descartan al salir.
@Composable
private fun bandejaModel(): BandejaNotificacionesViewModel {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return viewModel(viewModelStoreOwner = owner)
}

private val TIPOS = listOf(
    null to "Todos",
    "NUEVO_MENSAJE" to "Mensajes",
    "CAMBIO_ESTADO_PEDIDO" to "Cambios de estado",
    "NUEVA_COMPRA" to "Nuevas compras"
)

private val ESTADOS = listOf(
    null to "Todos",
    "PENDIENTE" to "Pendientes",
    "LEIDA" to "Leídas"
)

private val PRESETS_FECHA = listOf(
    "todas" to "Todas",
    "hoy" to "Hoy",
    "7" to "7 días",
    "30" to "30 días"
)

/** Selector compacto de un filtro de la bandeja (HU 53). */
@Composable
private fun SelectorFiltro(
    etiqueta: String,
    opciones: List<Pair<String?, String>>,
    valor: String?,
    onCambio: (String?) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    val actual = opciones.firstOrNull { it.first == valor }?.second ?: etiqueta
    TextButton(onClick = { abierto = true }) {
        Text("$etiqueta: $actual")
    }
    androidx.compose.material3.DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        opciones.forEach { (opcion, texto) ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(texto) },
                onClick = {
                    abierto = false
                    onCambio(opcion)
                }
            )
        }
    }
}

/**
 * HU 53/54 - Bandeja unificada de avisos con filtros combinables, paginación,
 * contador de pendientes y lectura individual o masiva.
 */
@Composable
fun BandejaNotificacionesScreen(
    onVerPedido: (Long) -> Unit,
    onVerVentas: () -> Unit,
    onVerConversacion: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val model = bandejaModel()
    LaunchedEffect(Unit) { model.cargar() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Notificaciones", style = MaterialTheme.typography.headlineSmall)

        // Mejora técnica Offline First: modo sin conexión, pendientes y avisos.
        IndicadorOffline(
            sinConexion = model.sinConexion,
            pendientes = model.pendientesSincronizacion,
            aviso = model.aviso ?: model.avisoSincronizacion,
            rechazo = model.rechazoSincronizacion
        )

        val datos = model.bandeja
        Text(
            if ((datos?.pendientes ?: 0L) > 0L) {
                "${datos!!.pendientes} notificación(es) pendiente(s) en toda tu bandeja."
            } else {
                "No tienes notificaciones pendientes."
            }
        )
        Button(
            onClick = { model.marcarTodas() },
            enabled = !model.accionando && (datos?.pendientes ?: 0L) > 0L
        ) { Text(if (model.accionando) "Marcando…" else "Marcar todas como leídas") }

        // HU 53 - Filtros combinables de tipo, estado y rango de fechas.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            SelectorFiltro("Tipo", TIPOS, model.tipo) { model.filtrar(it, model.estado, model.presetFecha) }
            SelectorFiltro("Estado", ESTADOS, model.estado) { model.filtrar(model.tipo, it, model.presetFecha) }
            SelectorFiltro("Fecha", PRESETS_FECHA, model.presetFecha) { model.filtrar(model.tipo, model.estado, it) }
        }

        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (model.cargando && datos == null) CircularProgressIndicator()
        if (!model.cargando && model.error == null && datos?.notificaciones?.isEmpty() == true) {
            Text("No hay notificaciones con los filtros seleccionados.")
        }

        datos?.notificaciones?.forEach { aviso ->
            AvisoTarjeta(
                aviso = aviso,
                accionando = model.accionando,
                onMarcarLeida = { model.marcarLeida(aviso.idNotificacion ?: return@AvisoTarjeta) },
                onVerDetalle = {
                    when (aviso.tipo) {
                        "NUEVO_MENSAJE" -> aviso.idConversacion?.let(onVerConversacion)
                        "NUEVA_COMPRA" -> onVerVentas()
                        else -> aviso.idPedido?.let(onVerPedido)
                    }
                }
            )
        }

        if (datos != null && datos.totalElementos > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { model.irAnterior() }, enabled = !model.cargando && model.pagina > 0) {
                    Text("Anterior")
                }
                Text(
                    "Página ${model.pagina + 1} de ${maxOf(datos.totalPaginas, 1)}" +
                        " · ${datos.totalElementos} notificación(es)"
                )
                TextButton(
                    onClick = { model.irSiguiente() },
                    enabled = !model.cargando && datos.tieneSiguiente
                ) { Text("Siguiente") }
            }
        }
    }
}

@Composable
private fun AvisoTarjeta(
    aviso: NotificacionResponse,
    accionando: Boolean,
    onMarcarLeida: () -> Unit,
    onVerDetalle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${if (aviso.estado == "LEIDA") "Leída" else "Pendiente"} · " +
                    resumenNotificacionVisible(aviso),
                fontWeight = FontWeight.SemiBold
            )
            Text(fechaHoraPedidoVisible(aviso.fecha), style = MaterialTheme.typography.bodySmall)
            aviso.fechaLectura?.let {
                Text("Leída el ${fechaHoraPedidoVisible(it)}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onVerDetalle) { Text("Ver detalle") }
                if (aviso.estado == "PENDIENTE") {
                    TextButton(onClick = onMarcarLeida, enabled = !accionando) {
                        Text(if (accionando) "Marcando…" else "Marcar como leída")
                    }
                }
            }
        }
    }
}
