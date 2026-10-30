package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.delay
import coil.compose.AsyncImage

private fun urlImagenProducto(url: String?): String? = when {
    url.isNullOrBlank() -> null
    url.startsWith("http://") || url.startsWith("https://") -> url
    else -> "http://10.0.2.2:9000${if (url.startsWith('/')) url else "/$url"}"
}

// El estado y los trabajos de red pertenecen a esta pantalla y se descartan al salir.
@Composable
private fun consultasModel(): ConsultasViewModel {
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return viewModel(viewModelStoreOwner = owner)
}

@Composable
fun ConsultaProducto(productoId: Long) {
    key(productoId) {
        val model = consultasModel()
        LaunchedEffect(Unit) { model.cargar(false) }
        Text("Consultar al vendedor", style = MaterialTheme.typography.titleMedium)
        FormularioMensaje(model, productoId, null)
    }
}

@Composable
private fun FormularioMensaje(model: ConsultasViewModel, productoId: Long?, conversacionId: Long?) {
    OutlinedTextField(value = model.texto, onValueChange = { model.texto = it }, label = { Text("Mensaje") },
        enabled = !model.enviando && model.maximo != null, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("${model.texto.length}/${model.maximo ?: 0} caracteres") })
    Button(onClick = { model.enviar(productoId, conversacionId) }, enabled = !model.enviando && model.maximo != null &&
        model.texto.trim().isNotEmpty() && model.texto.trim().length <= (model.maximo ?: 0)) {
        Text(if (model.enviando) "Enviando…" else "Enviar mensaje")
    }
    if (model.cargando || model.enviando) CircularProgressIndicator()
    model.exito?.let { Text(it) }
    model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (model.maximo == null && !model.cargando) TextButton(onClick = { model.cargar(conversacionId != null) }) { Text("Reintentar") }
}

@Composable
fun ConsultasScreen(esVendedor: Boolean, modifier: Modifier = Modifier,
    onVerProducto: (Long) -> Unit = {}) {
    // HU38 - Muestra el último mensaje y carga el historial cronológicamente por páginas.
    val model = consultasModel()
    var seleccion by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { model.cargar(true) }
    LaunchedEffect(seleccion) {
        while (true) {
            delay(15000)
            model.actualizarSilencioso(seleccion)
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Consultas", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { model.cargar(true) }, enabled = !model.cargando && !model.enviando) { Text("Actualizar consultas") }
        if (model.cargando) CircularProgressIndicator()
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!model.cargando && model.error == null && model.conversaciones.isEmpty()) Text("Aún no tienes consultas.")
        model.conversaciones.forEach { c ->
            TextButton(onClick = {
                seleccion = c.idConversacion
                model.texto = ""
                model.cargarMensajes(c.idConversacion)
            }, enabled = !model.enviando) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val imagen = urlImagenProducto(c.productoImagenUrl)
                    if (imagen != null) {
                        AsyncImage(model = imagen, contentDescription = "Imagen de ${c.productoNombre}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                    } else {
                        Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Box(contentAlignment = Alignment.Center) { Text("Sin imagen", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Column {
                        Text("Consulta #${c.idConversacion} — ${c.productoNombre}")
                        Text(c.ultimoMensaje?.contenido ?: "Sin mensajes", maxLines = 1)
                        if (c.productoDisponible == false) Text("Producto ya no disponible", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        model.conversaciones.find { it.idConversacion == seleccion }?.let { c ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val imagen = urlImagenProducto(c.productoImagenUrl)
                if (imagen != null) {
                    AsyncImage(model = imagen, contentDescription = "Imagen de ${c.productoNombre}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    Surface(modifier = Modifier.size(80.dp), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center) { Text("Sin imagen", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Column {
                    Text(c.productoNombre, style = MaterialTheme.typography.titleMedium)
                    if (c.productoDisponible != false) {
                        TextButton(onClick = { onVerProducto(c.productoId) }) { Text("Ver producto") }
                    } else {
                        Text("Producto ya no disponible", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (model.tieneMensajesAnteriores) {
                TextButton(onClick = { model.cargarMensajes(c.idConversacion, model.paginaMensajes + 1) },
                    enabled = !model.cargandoMensajes) {
                    Text(if (model.cargandoMensajes) "Cargando…" else "Cargar mensajes anteriores")
                }
            }
            if (model.cargandoMensajes) CircularProgressIndicator()
            model.errorMensajes?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            model.mensajes.forEach { m ->
                Text("${m.emisorRol} · ${m.fecha}")
                Text(m.contenido)
            }
            if (esVendedor) {
                Text("Responder consulta")
                FormularioMensaje(model, null, c.idConversacion)
            }
        }
    }
}
