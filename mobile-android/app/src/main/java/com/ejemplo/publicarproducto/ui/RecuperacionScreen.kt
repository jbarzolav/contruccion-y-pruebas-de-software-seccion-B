package com.ejemplo.publicarproducto.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** HU 22 - Recuperación y nueva contraseña al abrir el enlace temporal. */
@Composable
fun RecuperacionScreen(
    token: String?,
    onVolver: () -> Unit,
    onSolicitarEnlace: () -> Unit,
    viewModel: RecuperacionViewModel = viewModel()
) {
    LaunchedEffect(token) { viewModel.reiniciar() }
    BackHandler(onBack = onVolver)
    val cargando = viewModel.uiState is RecuperacionUiState.Loading
    val completado = viewModel.uiState is RecuperacionUiState.Success
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (token == null) "Recuperar acceso" else "Nueva contraseña",
                    style = MaterialTheme.typography.titleLarge)
                Text(if (token == null) "Ingresa tu correo para solicitar un enlace temporal."
                    else "Ingresa y confirma tu nueva contraseña.")
                if (token != null && token.isBlank()) {
                    Text("El enlace no contiene un token válido.", color = MaterialTheme.colorScheme.error)
                }
                if (!completado) {
                    if (token == null) {
                        OutlinedTextField(
                            value = viewModel.correo, onValueChange = viewModel::cambiarCorreo,
                            label = { Text("Correo") }, singleLine = true, enabled = !cargando,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            isError = viewModel.errores.containsKey("correo"), modifier = Modifier.fillMaxWidth())
                        viewModel.errores["correo"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    } else {
                        OutlinedTextField(
                            value = viewModel.password, onValueChange = viewModel::cambiarPassword,
                            label = { Text("Nueva contraseña") }, singleLine = true, enabled = !cargando,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = viewModel.errores.containsKey("password"), modifier = Modifier.fillMaxWidth())
                        viewModel.errores["password"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        OutlinedTextField(
                            value = viewModel.confirmacion, onValueChange = viewModel::cambiarConfirmacion,
                            label = { Text("Confirmar contraseña") }, singleLine = true, enabled = !cargando,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = viewModel.errores.containsKey("confirmacion"), modifier = Modifier.fillMaxWidth())
                        viewModel.errores["confirmacion"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    Button(onClick = { viewModel.enviar(token) },
                        enabled = !cargando && (token == null || token.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                        Text(if (cargando) "Enviando..." else if (token == null) "Solicitar enlace" else "Guardar contraseña")
                    }
                }
                when (val estado = viewModel.uiState) {
                    is RecuperacionUiState.Success -> Text(estado.mensaje)
                    is RecuperacionUiState.Error -> Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                TextButton(onClick = onVolver, enabled = !cargando) { Text("Volver a iniciar sesión") }
                if (token != null && !completado) {
                    TextButton(onClick = onSolicitarEnlace, enabled = !cargando) { Text("Solicitar un nuevo enlace") }
                }
            }
        }
    }
}
