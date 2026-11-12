package com.ejemplo.publicarproducto.view

import com.ejemplo.publicarproducto.domain.*
import com.ejemplo.publicarproducto.view.components.*
import com.ejemplo.publicarproducto.viewmodel.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * HU 21 - pantalla de inicio de sesión (Jetpack Compose, sin XML).
 *
 * La pantalla solo muestra el formulario y avisa del resultado: la
 * persistencia de la sesión (token/id/correo/rol/permisos) la hace
 * AuthRepositorio al confirmarse el login, nunca la vista. Sin token
 * guardado, esta pantalla es la entrada de la app.
 */
@Composable
fun LoginScreen(
    onLoginExitoso: () -> Unit,
    onRecuperarAcceso: () -> Unit = {},
    onCrearCuenta: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel()
) {
    // Al llegar a Success: la sesión ya quedó persistida por el repositorio.
    LaunchedEffect(viewModel.uiState) {
        val estado = viewModel.uiState
        if (estado is LoginUiState.Success) {
            viewModel.consumirExito() // HU 23 - No reutilizar un éxito de una sesión anterior.
            onLoginExitoso()
        }
    }

    val cargando = viewModel.uiState is LoginUiState.Loading
    val errorGeneral = (viewModel.uiState as? LoginUiState.Error)?.mensaje

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
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
                Text(
                    text = "Iniciar sesión",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = viewModel.correo,
                    onValueChange = viewModel::cambiarCorreo,
                    label = { Text("Correo") },
                    singleLine = true,
                    isError = viewModel.errorCorreo != null,
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                viewModel.errorCorreo?.let { mensaje ->
                    Text(
                        text = mensaje,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = viewModel::cambiarPassword,
                    label = { Text("Contraseña") },
                    singleLine = true,
                    isError = viewModel.errorPassword != null,
                    // Ocultamiento visual del texto
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                viewModel.errorPassword?.let { mensaje ->
                    Text(
                        text = mensaje,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.iniciarSesion() },
                    enabled = !cargando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (cargando) {
                            "Iniciando sesión..."
                        } else {
                            "Iniciar sesión"
                        }
                    )
                }

                // HU 22 - Acceso a la solicitud de recuperación.
                TextButton(onClick = onRecuperarAcceso, enabled = !cargando) {
                    Text("Olvidé mi contraseña")
                }

                // HU 26 - Acceso al registro de cuenta.
                TextButton(onClick = onCrearCuenta, enabled = !cargando) {
                    Text("Crear cuenta")
                }

                // Error general: 401 (credenciales) / 400 (validación) / red
                if (errorGeneral != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorGeneral,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
