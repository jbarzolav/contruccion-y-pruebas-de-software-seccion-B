package com.ejemplo.publicarproducto.view

import com.ejemplo.publicarproducto.domain.*
import com.ejemplo.publicarproducto.view.components.*
import com.ejemplo.publicarproducto.viewmodel.*
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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import coil.compose.AsyncImage
import com.ejemplo.publicarproducto.model.remote.ConfiguracionServidor

// El estado y los trabajos de red pertenecen a esta pantalla y se descartan al salir.
//
// R7 - Decisión de alcance: la pantalla de Consultas conserva su propio
// `ViewModelStore` a propósito. `ConsultaProducto` (el formulario de
// consulta dentro del detalle de un producto) necesita SU PROPIO
// `ConsultasViewModel`: si compartiera el de la pestaña, enviar un mensaje
// desde el detalle cambiaría el estado (selección, paginación y texto) de la
// pestaña de Consultas. El resto de pantallas usan el alcance de la
// `Activity` (`viewModel()`), que es el criterio mayoritario.
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
        // HU 36 - Consulta al vendedor: campo de consulta dentro del detalle
        // del producto, con la longitud maxima que fija el backend.
        Text("Consultar al vendedor", style = MaterialTheme.typography.titleMedium)
        FormularioMensaje(model, productoId, null)
    }
}

@Composable
private fun FormularioMensaje(model: ConsultasViewModel, productoId: Long?, conversacionId: Long?) {
    // HU 36 - nueva consulta (productoId) / HU 37 - respuesta del vendedor
    // (conversacionId): el mismo formulario sirve para ambos sentidos.
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
    onVerProducto: (Long) -> Unit = {}, conversacionInicial: Long? = null) {
    // HU38 - Muestra el último mensaje y carga el historial cronológicamente por páginas.
    val model = consultasModel()
    // HU 53 - Enlace desde la bandeja: abre la conversación indicada al cargar.
    var pendienteInicial by remember { mutableStateOf(conversacionInicial) }
    LaunchedEffect(Unit) { model.cargar(true) }
    LaunchedEffect(model.conversaciones) {
        val idInicial = pendienteInicial ?: return@LaunchedEffect
        if (model.conversaciones.any { it.idConversacion == idInicial }) {
            pendienteInicial = null
            model.seleccionarConversacion(idInicial)
        }
    }
    // R1/R3 - El sondeo de HU39 (15 s) vive en el ViewModel y SOLO corre
    // mientras la pantalla está STARTED: se detiene al pasar a segundo plano
    // y se reanuda al volver. Al salir de la pantalla se cancela también.
    // Así no se generan peticiones HTTP con la aplicación en segundo plano y
    // no hay dos sondeos de `GET /api/conversaciones` a la vez.
    LifecycleStartEffect(model) {
        model.iniciarSondeo()
        onStopOrDispose { model.detenerSondeo() }
    }
    val seleccion = model.conversacionSeleccionada
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Consultas", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { model.cargar(true) }, enabled = !model.cargando && !model.enviando) { Text("Actualizar consultas") }
        if (model.cargando) CircularProgressIndicator()
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!model.cargando && model.error == null && model.conversaciones.isEmpty()) Text("Aún no tienes consultas.")
        model.conversaciones.forEach { c ->
            // HU 40 - Cada conversacion muestra el producto al que pertenece
            // (imagen y nombre) para mantener el contexto de la consulta.
            TextButton(onClick = { model.seleccionarConversacion(c.idConversacion) }, enabled = !model.enviando) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val imagen = ConfiguracionServidor.urlImagen(c.productoImagenUrl)
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
                val imagen = ConfiguracionServidor.urlImagen(c.productoImagenUrl)
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
