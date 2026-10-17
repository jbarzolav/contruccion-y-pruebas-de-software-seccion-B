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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** HU 26 - Registro de cuenta desde la app; al crear la cuenta vuelve al login. */
@Composable
fun RegistroScreen(
    onVolver: () -> Unit,
    viewModel: RegistroViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.reiniciar() }
    BackHandler(onBack = onVolver)

    val cargando = viewModel.uiState is RegistroUiState.Loading
    val completado = viewModel.uiState is RegistroUiState.Success

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PulgaTec",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Crear cuenta", style = MaterialTheme.typography.titleLarge)

                Spacer(modifier = Modifier.height(16.dp))

                if (!completado) {
                    OutlinedTextField(
                        value = viewModel.correo, onValueChange = viewModel::cambiarCorreo,
                        label = { Text("Correo") }, singleLine = true, enabled = !cargando,
                        isError = viewModel.errorCorreo != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    viewModel.errorCorreo?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = viewModel.password, onValueChange = viewModel::cambiarPassword,
                        label = { Text("Contraseña") }, singleLine = true, enabled = !cargando,
                        isError = viewModel.errorPassword != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Mínimo 8 caracteres.")
                    viewModel.errorPassword?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = viewModel.confirmacion, onValueChange = viewModel::cambiarConfirmacion,
                        label = { Text("Confirmar contraseña") }, singleLine = true, enabled = !cargando,
                        isError = viewModel.errorConfirmacion != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    viewModel.errorConfirmacion?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.registrar() },
                        enabled = !cargando,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (cargando) "Creando cuenta..." else "Crear cuenta")
                    }
                }

                when (val estado = viewModel.uiState) {
                    is RegistroUiState.Success -> Text(estado.mensaje)
                    is RegistroUiState.Error ->
                        Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }

                TextButton(onClick = onVolver, enabled = !cargando) {
                    Text("Volver a iniciar sesión")
                }
            }
        }
    }
}
