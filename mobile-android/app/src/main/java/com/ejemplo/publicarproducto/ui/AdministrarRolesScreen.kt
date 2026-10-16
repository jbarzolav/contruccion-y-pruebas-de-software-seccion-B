package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** HU 23 - Asignación administrativa de roles con el diseño Compose existente. */
@Composable
fun AdministrarRolesScreen(onRolActualizado: () -> Unit, modifier: Modifier = Modifier,
        viewModel: AdministrarRolesViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.cargar() }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Administrar roles", style = MaterialTheme.typography.titleLarge)
        if (viewModel.cargando) Text("Cargando usuarios...")
        viewModel.mensaje?.let { Text(it, color = if (viewModel.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        viewModel.usuarios.forEach { usuario ->
            key(usuario.id) {
                var rol by remember(usuario.rol) { mutableStateOf(usuario.rol) }
                var abierto by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(usuario.correo)
                        Text("Rol actual: ${usuario.rol}")
                        Box {
                            TextButton(onClick = { abierto = true }, enabled = viewModel.guardando == null) { Text("Asignar rol: $rol") }
                            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                                listOf("CLIENTE", "VENDEDOR", "ADMINISTRADOR").forEach { opcion ->
                                    DropdownMenuItem(text = { Text(opcion) }, onClick = { rol = opcion; abierto = false })
                                }
                            }
                        }
                        Button(onClick = { viewModel.guardar(usuario, rol, onRolActualizado) },
                                enabled = viewModel.guardando == null && rol != usuario.rol) {
                            Text(if (viewModel.guardando == usuario.id) "Guardando..." else "Guardar rol")
                        }
                    }
                }
            }
        }
    }
}
