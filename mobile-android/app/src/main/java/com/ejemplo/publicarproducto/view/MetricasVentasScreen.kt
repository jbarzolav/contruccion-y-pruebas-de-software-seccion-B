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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.ejemplo.publicarproducto.model.ComparativaMetricaResponse
import com.ejemplo.publicarproducto.model.MetricasVentasResponse
import com.ejemplo.publicarproducto.model.ProductoVendidoResponse
import java.math.BigDecimal

/**
 * HU 57 - Panel de métricas de ventas del vendedor.
 *
 * Muestra los ingresos confirmados, el número de pedidos, el promedio por
 * pedido, los productos más vendidos y la comparación con el periodo
 * anterior (diferencia absoluta, variación porcentual y tendencia).
 */
@Composable
fun MetricasVentasScreen(
    modifier: Modifier = Modifier,
    viewModel: MetricasVentasViewModel = viewModel()
) {
    var desde by remember { mutableStateOf("") }
    var hasta by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.cargar() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Métricas de ventas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        IndicadorOffline(
            sinConexion = viewModel.sinConexion,
            pendientes = viewModel.pendientesSincronizacion,
            aviso = viewModel.avisoSincronizacion,
            rechazo = viewModel.rechazoSincronizacion
        )

        SelectorFiltro(
            etiqueta = "Periodo",
            seleccionado = viewModel.preset,
            opciones = MetricasVentasViewModel.PERIODOS
        ) { viewModel.cambiarPreset(it) }

        if (viewModel.preset == MetricasVentasViewModel.PRESET_PERSONALIZADO) {
            OutlinedTextField(
                value = desde,
                onValueChange = { desde = it.take(10) },
                label = { Text("Desde (AAAA-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = hasta,
                onValueChange = { hasta = it.take(10) },
                label = { Text("Hasta (AAAA-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.aplicarPeriodoPersonalizado(desde, hasta) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Aplicar periodo") }
        }

        viewModel.errorPeriodo?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.cambiarComparacion(!viewModel.comparar) }) {
                Text(if (viewModel.comparar) "Comparar con el periodo anterior: sí" else "Comparar con el periodo anterior: no")
            }
        }

        viewModel.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::cargar, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
        }

        if (viewModel.cargando && viewModel.metricas == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        val panel = viewModel.metricas
        if (!viewModel.cargando && panel == null && viewModel.error == null) {
            Text("Aún no hay métricas guardadas en el dispositivo.")
        }

        panel?.let { datos ->
            Text(
                "Periodo analizado: ${datos.desde ?: "-"} al ${datos.hasta ?: "-"}" +
                    if (datos.compararPeriodoAnterior == true && datos.desdeAnterior != null) {
                        " · Periodo anterior: ${datos.desdeAnterior} al ${datos.hastaAnterior}"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall
            )

            val periodo = datos.periodoActual
            if (periodo == null || (periodo.cantidadPedidos ?: 0L) == 0L) {
                Text("No hay ventas confirmadas en el periodo seleccionado.")
            }

            MetricaCard(
                titulo = "Ingresos confirmados",
                valor = "S/ ${moneda(periodo?.ingresosConfirmados)}",
                comparativa = datos.comparativaIngresos,
                esMoneda = true,
                anterior = datos.periodoAnterior?.ingresosConfirmados
            )
            MetricaCard(
                titulo = "N.º de pedidos",
                valor = (periodo?.cantidadPedidos ?: 0L).toString(),
                comparativa = datos.comparativaPedidos,
                esMoneda = false,
                anterior = datos.periodoAnterior?.cantidadPedidos?.let { BigDecimal.valueOf(it) }
            )
            MetricaCard(
                titulo = "Promedio por pedido",
                valor = "S/ ${moneda(periodo?.promedioPorPedido)}",
                comparativa = datos.comparativaPromedio,
                esMoneda = true,
                anterior = datos.periodoAnterior?.promedioPorPedido
            )

            Text("Productos más vendidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val productos = datos.productosMasVendidos.orEmpty()
            if (productos.isEmpty()) {
                Text("No hay productos vendidos en el periodo seleccionado.")
            } else {
                productos.forEach { producto -> ProductoVendidoFila(producto) }
            }
        }
    }
}

@Composable
private fun MetricaCard(
    titulo: String,
    valor: String,
    comparativa: ComparativaMetricaResponse?,
    esMoneda: Boolean,
    anterior: BigDecimal?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (comparativa != null) {
                Text(
                    tendenciaVisible(comparativa.tendencia) + " · " + variacionVisible(comparativa.variacionPorcentual),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorTendencia(comparativa.tendencia)
                )
                val diferencia = comparativa.diferenciaAbsoluta
                Text(
                    "Diferencia: " + (if (esMoneda) "S/ ${moneda(diferencia)}" else diferencia?.toPlainString() ?: "0"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (anterior != null) {
                Text(
                    "Periodo anterior: " + (if (esMoneda) "S/ ${moneda(anterior)}" else anterior.toPlainString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProductoVendidoFila(producto: ProductoVendidoResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(producto.nombre.orEmpty(), fontWeight = FontWeight.SemiBold)
            Text(
                "${producto.unidadesVendidas ?: 0L} unidad(es) vendida(s) · S/ ${moneda(producto.importeAtribuido)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Formatea un importe con dos decimales. */
internal fun moneda(valor: BigDecimal?): String =
    (valor ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

/** Texto de la tendencia de la comparativa. */
internal fun tendenciaVisible(tendencia: String?): String = when (tendencia) {
    "AUMENTO" -> "Aumento"
    "DISMINUCION" -> "Disminución"
    "IGUAL" -> "Sin cambios"
    else -> ""
}

/** Variación porcentual; sin base de comparación cuando el valor es nulo. */
internal fun variacionVisible(variacion: BigDecimal?): String {
    if (variacion == null) return "sin base de comparación"
    val valor = variacion.setScale(2, java.math.RoundingMode.HALF_UP)
    val signo = if (valor.signum() > 0) "+" else ""
    return "$signo${valor.toPlainString()} %"
}

/** Color de la tendencia: verde si sube, rojo si baja. */
@Composable
internal fun colorTendencia(tendencia: String?) = when (tendencia) {
    "AUMENTO" -> MaterialTheme.colorScheme.primary
    "DISMINUCION" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
