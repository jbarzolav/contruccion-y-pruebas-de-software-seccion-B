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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** HU24 - Auditoría administrativa en Compose, sin datos de sesión ni credenciales. */
@Composable
fun AuditoriaScreen(modifier: Modifier = Modifier, viewModel: AuditoriaViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.cargar() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Auditoría del sistema", style = MaterialTheme.typography.titleLarge)
        when (val estado = viewModel.estado) {
            AuditoriaUiState.Initial, AuditoriaUiState.Loading -> Text("Cargando registros...")
            is AuditoriaUiState.Error -> {
                Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.cargar() }) { Text("Reintentar") }
            }
            is AuditoriaUiState.Success -> {
                val pagina = estado.pagina
                if (pagina.contenido.isEmpty()) Text("No hay registros de auditoría.")
                pagina.contenido.forEach { registro ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(registro.accion, style = MaterialTheme.typography.titleMedium)
                            Text("Usuario: ${registro.usuario}")
                            Text("Fecha: ${fechaVisible(registro.fecha)}")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${pagina.totalElementos} registros · página ${pagina.pagina + 1} de ${pagina.totalPaginas.coerceAtLeast(1)}")
                    Row {
                        TextButton(onClick = { viewModel.cargar(pagina.pagina - 1) }, enabled = pagina.pagina > 0) {
                            Text("Anterior")
                        }
                        TextButton(onClick = { viewModel.cargar(pagina.pagina + 1) },
                            enabled = pagina.pagina + 1 < pagina.totalPaginas) { Text("Siguiente") }
                    }
                }
            }
        }
    }
}

private fun fechaVisible(valor: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault()).format(Instant.parse(valor))
}.getOrDefault("Fecha no disponible")
