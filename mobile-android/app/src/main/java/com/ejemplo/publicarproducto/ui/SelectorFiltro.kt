package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalDate

@Composable
internal fun SelectorFiltro(
    etiqueta: String,
    seleccionado: String,
    opciones: List<Pair<String, String>>,
    alSeleccionar: (String) -> Unit
) {
    var abierto by remember(etiqueta) { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (seleccionado.isBlank()) etiqueta else opciones.firstOrNull { it.first == seleccionado }?.second ?: etiqueta)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (valor, texto) ->
                DropdownMenuItem(text = { Text(texto) }, onClick = {
                    alSeleccionar(valor)
                    abierto = false
                })
            }
        }
    }
}

internal fun fechaFiltroValida(fecha: String): Boolean = fecha.isBlank() ||
    runCatching { LocalDate.parse(fecha) }.isSuccess
