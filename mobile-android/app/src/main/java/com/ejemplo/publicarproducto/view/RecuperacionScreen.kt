package com.ejemplo.publicarproducto.view

import com.ejemplo.publicarproducto.domain.*
import com.ejemplo.publicarproducto.view.components.*
import com.ejemplo.publicarproducto.viewmodel.*
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

/**
 * HU 22 - Recuperación de contraseña en tres pasos, igual que el web:
 *
 * 1. CORREO: "Solicitar código" (llega un código de 6 dígitos por correo).
 * 2. CODIGO: "Verificar código" (los 6 dígitos).
 * 3. CONTRASENA: "Guardar contraseña" con el código ya verificado.
 *
 * El enlace del correo (pulgatec://restablecer?token=) trae ese mismo código
 * y se verifica automáticamente, igual que al abrir /restablecer en el web.
 */
@Composable
fun RecuperacionScreen(
    token: String?,
    onVolver: () -> Unit,
    viewModel: RecuperacionViewModel = viewModel()
) {
    LaunchedEffect(token) {
        viewModel.reiniciar()
        if (token != null) viewModel.validarEnlace(token)
    }
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
                // Título y descripción del paso actual (mismos textos que el web).
                Text(
                    if (viewModel.paso == PasoRecuperacion.CORREO) "Recuperar acceso"
                    else "Nueva contraseña",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    when (viewModel.paso) {
                        PasoRecuperacion.CORREO ->
                            "Ingresa tu correo para recibir un código temporal de recuperación."
                        PasoRecuperacion.CODIGO ->
                            "Ingresa el código de verificación que enviamos a tu correo."
                        PasoRecuperacion.CONTRASENA ->
                            if (viewModel.codigoVerificado)
                                "Código verificado. Ingresa y confirma tu nueva contraseña."
                            else "Ingresa el código de verificación que enviamos a tu correo."
                    }
                )

                if (!completado) {
                    when (viewModel.paso) {
                        // ------------------------------------------------ paso 1
                        PasoRecuperacion.CORREO -> {
                            OutlinedTextField(
                                value = viewModel.correo, onValueChange = viewModel::cambiarCorreo,
                                label = { Text("Correo") }, singleLine = true, enabled = !cargando,
                                placeholder = { Text("tucorreo@ejemplo.com") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                isError = viewModel.errores.containsKey("correo"),
                                modifier = Modifier.fillMaxWidth()
                            )
                            viewModel.errores["correo"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                onClick = viewModel::solicitarCodigo,
                                enabled = !cargando, modifier = Modifier.fillMaxWidth()
                            ) { Text(if (cargando) "Enviando..." else "Solicitar código") }
                        }

                        // ------------------------------------------------ paso 2
                        PasoRecuperacion.CODIGO -> {
                            OutlinedTextField(
                                value = viewModel.codigo, onValueChange = viewModel::cambiarCodigo,
                                label = { Text("Código de verificación") }, singleLine = true, enabled = !cargando,
                                placeholder = { Text("123456") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = viewModel.errores.containsKey("codigo"),
                                modifier = Modifier.fillMaxWidth()
                            )
                            viewModel.errores["codigo"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                onClick = viewModel::verificarCodigo,
                                enabled = !cargando, modifier = Modifier.fillMaxWidth()
                            ) { Text(if (cargando) "Verificando..." else "Verificar código") }
                        }

                        // ------------------------------------------------ paso 3
                        PasoRecuperacion.CONTRASENA -> {
                            Text(
                                "Código verificado: ${viewModel.codigo.trim()}",
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = viewModel.password, onValueChange = viewModel::cambiarPassword,
                                label = { Text("Nueva contraseña") }, singleLine = true, enabled = !cargando,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                isError = viewModel.errores.containsKey("password"),
                                modifier = Modifier.fillMaxWidth()
                            )
                            viewModel.errores["password"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            OutlinedTextField(
                                value = viewModel.confirmacion, onValueChange = viewModel::cambiarConfirmacion,
                                label = { Text("Confirmar contraseña") }, singleLine = true, enabled = !cargando,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                isError = viewModel.errores.containsKey("confirmacion"),
                                modifier = Modifier.fillMaxWidth()
                            )
                            viewModel.errores["confirmacion"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                onClick = viewModel::guardarPassword,
                                enabled = !cargando, modifier = Modifier.fillMaxWidth()
                            ) { Text(if (cargando) "Enviando..." else "Guardar contraseña") }
                            TextButton(onClick = viewModel::usarOtroCodigo, enabled = !cargando) {
                                Text("Usar otro código")
                            }
                        }
                    }
                }

                // Aviso de éxito o de error del último servicio llamado.
                when (val estado = viewModel.uiState) {
                    is RecuperacionUiState.Success -> Text(estado.mensaje)
                    is RecuperacionUiState.Error -> Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }

                // Tras solicitar el código se pasa al paso de verificación.
                if (viewModel.paso == PasoRecuperacion.CORREO && completado) {
                    Button(
                        onClick = viewModel::irAlCodigo,
                        enabled = !cargando, modifier = Modifier.fillMaxWidth()
                    ) { Text("Ingresar el código") }
                }

                TextButton(onClick = onVolver, enabled = !cargando) { Text("Volver a iniciar sesión") }

                // Mismo enlace que el web para pedir otro código desde los pasos 2 y 3.
                if (viewModel.paso != PasoRecuperacion.CORREO && !completado) {
                    TextButton(onClick = viewModel::volverAlCorreo, enabled = !cargando) {
                        Text("Solicitar un nuevo código")
                    }
                }
            }
        }
    }
}
