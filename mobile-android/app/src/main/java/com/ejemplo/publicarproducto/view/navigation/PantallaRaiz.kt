package com.ejemplo.publicarproducto.view.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ejemplo.publicarproducto.view.LoginScreen
import com.ejemplo.publicarproducto.view.RecuperacionScreen
import com.ejemplo.publicarproducto.view.RegistroScreen
import com.ejemplo.publicarproducto.view.components.DialogoCerrarSesion
import com.ejemplo.publicarproducto.viewmodel.AvisosConversacionViewModel
import com.ejemplo.publicarproducto.viewmodel.LogoutUiState
import com.ejemplo.publicarproducto.viewmodel.SesionViewModel

/**
 * Raíz de la interfaz de PulgaTec.
 *
 * Decide qué mostrar según el estado de sesión publicado por
 * [SesionViewModel]: recuperación de acceso (HU 22), validación de sesión,
 * registro (HU 26), inicio de sesión (HU 21) o la navegación principal.
 */
@Composable
fun PantallaRaiz(
    sesionViewModel: SesionViewModel,
    avisosConversacionViewModel: AvisosConversacionViewModel,
    tokenRecuperacion: String?,
    recuperando: Boolean,
    registrando: Boolean,
    onEstadoRecuperacion: (token: String?, activa: Boolean) -> Unit,
    onEstadoRegistro: (activa: Boolean) -> Unit
) {
    var mostrarDialogoLogout by remember { mutableStateOf(false) }
    val usuario = sesionViewModel.usuario

    Column(modifier = Modifier.fillMaxSize()) {
        if (usuario != null) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    sesionViewModel.reiniciarEstadoLogout()
                    mostrarDialogoLogout = true
                }) { Text("Cerrar sesión") }
            }
        }
        sesionViewModel.aviso?.let { aviso ->
            Text(aviso, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { sesionViewModel.cerrarAviso() }) { Text("Cerrar aviso") }
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                recuperando -> RecuperacionScreen(
                    token = tokenRecuperacion,
                    onVolver = {
                        onEstadoRecuperacion(null, false)
                        sesionViewModel.volverAlLogin()
                    },
                    onSolicitarEnlace = { onEstadoRecuperacion(null, false) }
                )

                sesionViewModel.validando -> Text("Validando sesión...")

                registrando -> RegistroScreen(
                    onVolver = {
                        onEstadoRegistro(false)
                        sesionViewModel.volverAlLogin()
                    },
                    onCuentaCreada = {
                        // HU 26 - La sesión ya la persistió
                        // AuthRepositorio; aquí solo se entra al flujo.
                        onEstadoRegistro(false)
                        sesionViewModel.recargarYValidar()
                    }
                )

                usuario == null -> LoginScreen(
                    onLoginExitoso = { sesionViewModel.recargarYValidar() },
                    onRecuperarAcceso = {
                        onEstadoRecuperacion(null, true)
                    },
                    onCrearCuenta = { onEstadoRegistro(true) }
                )

                else -> AppNavegacion(
                    usuario = usuario,
                    sesionViewModel = sesionViewModel,
                    avisosConversacionViewModel = avisosConversacionViewModel
                )
            }
        }
    }

    if (mostrarDialogoLogout) {
        DialogoCerrarSesion(
            estado = sesionViewModel.logoutState,
            onConfirmar = { sesionViewModel.cerrarSesion() },
            onDismiss = {
                if (sesionViewModel.logoutState !is LogoutUiState.Loading) {
                    mostrarDialogoLogout = false
                    sesionViewModel.reiniciarEstadoLogout()
                }
            },
            onCancelar = { mostrarDialogoLogout = false }
        )
    }
}
