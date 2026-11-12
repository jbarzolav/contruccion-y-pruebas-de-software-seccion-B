package com.ejemplo.publicarproducto.view.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ejemplo.publicarproducto.viewmodel.LogoutUiState

/**
 * Diálogo de cierre de sesión (HU 25).
 *
 * Solo presenta el estado que publica `SesionViewModel` y devuelve la
 * intención del usuario; la operación de cerrar sesión la ejecuta el
 * ViewModel.
 */
@Composable
fun DialogoCerrarSesion(
    estado: LogoutUiState,
    onConfirmar: () -> Unit,
    onDismiss: () -> Unit,
    onCancelar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar sesión") },
        text = {
            when (estado) {
                LogoutUiState.Initial -> Text("¿Quieres cerrar tu sesión de forma segura?")
                LogoutUiState.Loading -> Text("Cerrando sesión...")
                LogoutUiState.Success -> Text("Sesión cerrada correctamente.")
                is LogoutUiState.Error -> Text(estado.mensaje, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            when (estado) {
                LogoutUiState.Initial -> TextButton(onClick = onConfirmar) { Text("Cerrar sesión") }
                LogoutUiState.Loading -> Unit
                LogoutUiState.Success, is LogoutUiState.Error -> TextButton(onClick = onDismiss) {
                    Text("Continuar")
                }
            }
        },
        dismissButton = {
            if (estado == LogoutUiState.Initial) TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
