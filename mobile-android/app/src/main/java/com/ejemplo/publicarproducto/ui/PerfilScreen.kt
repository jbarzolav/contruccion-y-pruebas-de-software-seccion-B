package com.ejemplo.publicarproducto.ui

import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.ejemplo.publicarproducto.model.PerfilUsuarioResponse
import com.ejemplo.publicarproducto.model.PerfilVendedorResponse

/**
 * HU 27 - Pantalla "Mi perfil" (Android, Jetpack Compose).
 *
 * HU 28 - Muestra los datos personales y permite entrar en modo edición
 * (nombre, correo, teléfono) con validación local, Guardar/Cancelary
 * refresco con la respuesta del backend.
 *
 * HU 29 - Si la sesión es VENDEDOR, agrega la sección "Información del
 * vendedor" con carga, edición y guardado del perfil comercial.
 */
@Composable
fun PerfilScreen(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<PerfilViewModel>()
    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        viewModel.seleccionarFoto(it)
    }
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
                if (viewModel.editando) {
                    FormularioEdicionPerfil(viewModel)
                } else {
                    PerfilContenido(estado.perfil, viewModel.fotoLocalUri)
                    viewModel.avisoEdicion?.let { mensaje ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = mensaje, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.iniciarEdicion() }) {
                        Text("Editar datos personales")
                    }
                }

                // HU 30 - selección, vista previa y guardado de la foto personal.
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { selectorFoto.launch("image/*") },
                    enabled = !viewModel.subiendoFoto
                ) {
                    Text(if (viewModel.fotoLocalUri == null) "Elegir foto JPG o PNG" else "Cambiar foto")
                }
                if (viewModel.fotoLocalUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.subirFoto() }, enabled = !viewModel.subiendoFoto) {
                        Text(if (viewModel.subiendoFoto) "Guardando foto..." else "Actualizar foto")
                    }
                }
                if (viewModel.subiendoFoto) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
                viewModel.avisoFoto?.let { mensaje ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mensaje,
                        color = if (mensaje.startsWith("Foto de perfil")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }

                // HU 29 - la sección del vendedor solo aplica a ese rol.
                if (estado.perfil.rol == "VENDEDOR") {
                    Spacer(modifier = Modifier.height(16.dp))
                    SeccionVendedor(viewModel)
                }
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
            enabled = !viewModel.editando,
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Text("Volver")
        }
    }
}

@Composable
private fun PerfilContenido(perfil: PerfilUsuarioResponse, fotoLocalUri: Uri?) {
    val nombre = perfil.nombre?.takeIf { it.isNotBlank() } ?: "Mi cuenta"
    val inicial = nombre.firstOrNull()?.uppercaseChar() ?: 'U'
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
                    val fotoGuardada = perfil.fotoUrl?.let { ruta ->
                        if (ruta.startsWith("http")) ruta else "http://10.0.2.2:9000$ruta"
                    }
                    val modeloFoto: Any? = fotoLocalUri ?: fotoGuardada
                    if (modeloFoto != null) {
                        AsyncImage(
                            model = modeloFoto,
                            contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(text = inicial.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = nombre,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            DatosFila("Nombre", nombre.takeUnless { it == "Mi cuenta" } ?: "Sin nombre registrado")
            DatosFila("Correo electrónico", perfil.correo)
            DatosFila("Teléfono", perfil.telefono?.takeIf { it.isNotBlank() } ?: "Sin teléfono registrado")
        }
    }
}

/**
 * HU 28 - Formulario de edición de los campos personales habilitados.
 * El rol, los permisos y la contraseña no se muestran como editables.
 */
@Composable
private fun FormularioEdicionPerfil(viewModel: PerfilViewModel) {
    val guardando = viewModel.guardando
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Editar datos personales", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = viewModel.nombre,
                onValueChange = viewModel::cambiarNombre,
                label = { Text("Nombre") },
                singleLine = true,
                enabled = !guardando,
                isError = viewModel.errorNombre != null,
                modifier = Modifier.fillMaxWidth()
            )
            viewModel.errorNombre?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = viewModel.correo,
                onValueChange = viewModel::cambiarCorreo,
                label = { Text("Correo electrónico") },
                singleLine = true,
                enabled = !guardando,
                isError = viewModel.errorCorreo != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            viewModel.errorCorreo?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = viewModel.telefono,
                onValueChange = viewModel::cambiarTelefono,
                label = { Text("Teléfono (opcional)") },
                singleLine = true,
                enabled = !guardando,
                isError = viewModel.errorTelefono != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            viewModel.errorTelefono?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            viewModel.avisoEdicion?.let { mensaje ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = mensaje, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = { viewModel.guardarCambios() },
                    enabled = !guardando,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (guardando) "Guardando..." else "Guardar")
                }
                Spacer(modifier = Modifier.size(12.dp))
                Button(
                    onClick = { viewModel.cancelarEdicion() },
                    enabled = !guardando,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

/**
 * HU 29 - Sección "Información del vendedor" (solo rol VENDEDOR).
 * Carga el perfil comercial, permite completarlo/editarlo y guardarlo.
 */
@Composable
private fun SeccionVendedor(viewModel: PerfilViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Información del vendedor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            when {
                viewModel.cargandoVendedor -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                viewModel.editandoVendedor -> {
                    FormularioVendedor(viewModel)
                }

                else -> {
                    val perfil = viewModel.perfilVendedor
                    val sinCompletar = perfil?.nombreComercial.isNullOrBlank()
                    if (sinCompletar) {
                        Text(
                            text = "Aún no has completado tu información comercial. " +
                                "Complétala para generar más confianza en tus clientes.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    DatosFila("Nombre comercial", perfil?.nombreComercial?.takeIf { it.isNotBlank() } ?: "Sin completar")
                    DatosFila("Descripción", perfil?.descripcion?.takeIf { it.isNotBlank() } ?: "Sin completar")
                    DatosFila("Teléfono de contacto", perfil?.telefonoContacto?.takeIf { it.isNotBlank() } ?: "Sin completar")

                    viewModel.avisoVendedor?.let { mensaje ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = mensaje, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.iniciarEdicionVendedor() }) {
                        Text(if (sinCompletar) "Completar información" else "Editar información")
                    }
                }
            }
        }
    }
}

@Composable
private fun FormularioVendedor(viewModel: PerfilViewModel) {
    val guardando = viewModel.guardandoVendedor

    OutlinedTextField(
        value = viewModel.nombreComercial,
        onValueChange = viewModel::cambiarNombreComercial,
        label = { Text("Nombre comercial") },
        singleLine = true,
        enabled = !guardando,
        isError = viewModel.errorNombreComercial != null,
        modifier = Modifier.fillMaxWidth()
    )
    viewModel.errorNombreComercial?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = viewModel.descripcion,
        onValueChange = viewModel::cambiarDescripcion,
        label = { Text("Descripción") },
        enabled = !guardando,
        isError = viewModel.errorDescripcion != null,
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    viewModel.errorDescripcion?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = viewModel.telefonoContacto,
        onValueChange = viewModel::cambiarTelefonoContacto,
        label = { Text("Teléfono de contacto") },
        singleLine = true,
        enabled = !guardando,
        isError = viewModel.errorTelefonoContacto != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )
    viewModel.errorTelefonoContacto?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    viewModel.avisoVendedor?.let { mensaje ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = mensaje, color = MaterialTheme.colorScheme.error)
    }

    Spacer(modifier = Modifier.height(16.dp))
    Row {
        Button(
            onClick = { viewModel.guardarPerfilVendedor() },
            enabled = !guardando,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (guardando) "Guardando..." else "Guardar")
        }
        Spacer(modifier = Modifier.size(12.dp))
        Button(
            onClick = { viewModel.cancelarEdicionVendedor() },
            enabled = !guardando,
            modifier = Modifier.weight(1f)
        ) {
            Text("Cancelar")
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
