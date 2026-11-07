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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.ejemplo.publicarproducto.model.UmbralStockResponse

/**
 * HU 58 - Panel de umbrales de stock y avisos de stock bajo del vendedor.
 *
 * El aviso se emite SOLO en el cruce descendente del umbral y se rearma tras
 * una reposición. Al configurar un umbral con el stock ya por debajo no se
 * genera un aviso inmediato: el panel muestra el estado "Stock bajo".
 */
@Composable
fun UmbralesStockScreen(
    modifier: Modifier = Modifier,
    viewModel: UmbralesStockViewModel = viewModel(),
    onEditarPublicacion: (Long) -> Unit = {},
    onVerPublicacion: (Long) -> Unit = {}
) {
    LaunchedEffect(Unit) { viewModel.cargar() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Umbrales de stock", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Text(
            "Configura el mínimo de unidades que quieres mantener en cada publicación. " +
                "Te avisaremos cuando el stock cruce ese umbral (una sola vez por cada bajada); " +
                "si repones el stock, la alerta se rearma para el siguiente cruce.",
            style = MaterialTheme.typography.bodySmall
        )

        IndicadorOffline(
            sinConexion = viewModel.sinConexion,
            pendientes = viewModel.pendientesSincronizacion,
            aviso = viewModel.avisoSincronizacion,
            rechazo = viewModel.rechazoSincronizacion
        )

        viewModel.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::cargar, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
        }
        viewModel.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        if (viewModel.cargando && viewModel.publicaciones.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        if (!viewModel.cargando && viewModel.publicaciones.isEmpty() && viewModel.error == null) {
            Text("Todavía no tienes publicaciones publicadas.")
        }

        viewModel.publicaciones.forEach { publicacion ->
            PublicacionUmbralCard(
                publicacion = publicacion,
                pendiente = viewModel.pendientes[publicacion.idProducto],
                guardando = viewModel.guardandoId == publicacion.idProducto,
                onGuardar = { valor ->
                    publicacion.idProducto?.let { id -> viewModel.guardarUmbral(id, valor) }
                },
                onEditar = { publicacion.idProducto?.let(onEditarPublicacion) },
                onVer = { publicacion.idProducto?.let(onVerPublicacion) }
            )
        }

        Text("Avisos de stock bajo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (viewModel.avisos.isEmpty()) {
            Text("No tienes avisos de stock bajo por ahora.")
        } else {
            viewModel.avisos.forEach { aviso ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            aviso.nombre?.let { "Stock bajo en «$it»" } ?: "Stock bajo en una publicación",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Stock actual: ${aviso.stockActual ?: "-"} · " +
                                "Umbral: ${aviso.umbral ?: "-"} · ${fechaHoraPedidoVisible(aviso.fecha)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        aviso.idProducto?.let { id ->
                            OutlinedButton(onClick = { onVerPublicacion(id) }) { Text("Ver publicación") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicacionUmbralCard(
    publicacion: UmbralStockResponse,
    pendiente: Int?,
    guardando: Boolean,
    onGuardar: (String) -> Unit,
    onEditar: () -> Unit,
    onVer: () -> Unit
) {
    var valor by remember(publicacion.idProducto, publicacion.umbral) {
        mutableStateOf(publicacion.umbral?.toString() ?: "")
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(publicacion.nombre.orEmpty(), fontWeight = FontWeight.Bold)
            Text("Stock actual: ${publicacion.stock ?: 0} unidad(es)")

            if (publicacion.umbral == null) {
                Text("Sin umbral configurado", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Umbral configurado: ${publicacion.umbral}")
            }

            if (publicacion.stockBajo == true) {
                Text(
                    "Stock bajo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (publicacion.alertaNotificada == true) {
                Text(
                    "Aviso ya notificado (espera una reposición para volver a avisar)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mejora técnica Offline First: el umbral propuesto en el
            // dispositivo se muestra como pendiente hasta que el servidor lo
            // confirme.
            pendiente?.let {
                Text(
                    "Umbral pendiente de sincronizar: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = valor,
                onValueChange = { valor = it.filter { caracter -> caracter.isDigit() }.take(6) },
                label = { Text("Umbral mínimo (0 o mayor)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onGuardar(valor) },
                    enabled = !guardando,
                    modifier = Modifier.weight(1f)
                ) { Text(if (guardando) "Guardando..." else "Guardar umbral") }
                OutlinedButton(onClick = onEditar, modifier = Modifier.weight(1f)) {
                    Text("Gestionar publicación")
                }
                OutlinedButton(onClick = onVer, modifier = Modifier.weight(1f)) {
                    Text("Ver publicación")
                }
            }
        }
    }
}
