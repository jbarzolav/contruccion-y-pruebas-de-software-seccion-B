package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejemplo.publicarproducto.model.UsuarioRolResponse

/**
 * HU 27 - Pantalla "Mi perfil" (Android, Jetpack Compose).
 */
@Composable
fun PerfilScreen(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<PerfilViewModel>()
    LaunchedEffect(Unit) { viewModel.cargarPerfil() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Mi perfil",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (val estado = viewModel.estado) {
            PerfilUiState.Cargando -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PerfilUiState.Exito -> {
                PerfilContenido(estado.perfil)
            }
            is PerfilUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = estado.mensaje,
                            color = Color(0xFF991B1B),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.cargarPerfil() }) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onVolver,
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Text("Volver")
        }
    }
}

@Composable
private fun PerfilContenido(perfil: UsuarioRolResponse) {
    val inicial = perfil.correo.trim().firstOrNull()?.uppercaseChar() ?: 'U'
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = inicial.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = perfil.correo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "Rol: ${perfil.rol}", color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            DatosFila("ID de usuario", perfil.id.toString())
            DatosFila("Correo electrónico", perfil.correo)
            DatosFila("Rol", perfil.rol)
            DatosFilaPermisos(perfil.permisos)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Este perfil muestra únicamente tus datos autorizados.",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DatosFila(etiqueta: String, valor: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = etiqueta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = valor, color = Color.DarkGray, fontSize = 14.sp)
    }
}

@Composable
private fun DatosFilaPermisos(permisos: Set<String>) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = "Permisos", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        if (permisos.isEmpty()) {
            Text(text = "No hay permisos asignados", color = Color.DarkGray, fontSize = 14.sp)
        } else {
            permisos.forEach { p -> Text(text = "• $p", color = Color.DarkGray, fontSize = 14.sp) }
        }
    }
}
