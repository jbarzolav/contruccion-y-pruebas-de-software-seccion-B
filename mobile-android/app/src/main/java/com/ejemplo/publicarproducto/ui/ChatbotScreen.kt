
package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ejemplo.publicarproducto.model.ProductoResumenResponse

/**
 * HU 16 - Chatbot de recomendaciones.
 * HU 17 - Consultas técnicas sobre componentes electrónicos.
 */
@Composable
fun ChatbotScreen(
    onSeleccionarProducto: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatbotViewModel = viewModel()
) {
    var consulta by remember { mutableStateOf("") }
    val listaState = rememberLazyListState()

    LaunchedEffect(viewModel.mensajes.size) {
        val ultimo = viewModel.mensajes.indices.lastOrNull() ?: 0
        listaState.animateScrollToItem(ultimo)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🤖 Asistente PulgaTec",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Busca productos o resuelve dudas sobre componentes electrónicos.",
            style = MaterialTheme.typography.bodyMedium
        )

        // HU 16 y HU 17 - Selección del modo del chatbot.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = viewModel.modo == ModoChatbot.RECOMENDACIONES,
                onClick = {
                    viewModel.cambiarModo(ModoChatbot.RECOMENDACIONES)
                },
                label = { Text("🛒 Recomendaciones") },
                enabled = !viewModel.enviando,
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = viewModel.modo == ModoChatbot.CONSULTAS_TECNICAS,
                onClick = {
                    viewModel.cambiarModo(ModoChatbot.CONSULTAS_TECNICAS)
                },
                label = { Text("🔧 Consultas técnicas") },
                enabled = !viewModel.enviando,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            state = listaState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(viewModel.mensajes) { mensaje ->
                BurbujaMensaje(
                    mensaje = mensaje,
                    onSeleccionarProducto = onSeleccionarProducto
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = consulta,
                onValueChange = { consulta = it },
                placeholder = {
                    Text(
                        if (viewModel.modo == ModoChatbot.CONSULTAS_TECNICAS) {
                            "¿Para qué sirve una protoboard?"
                        } else {
                            "Escribe qué producto buscas..."
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    viewModel.enviarConsulta(consulta)
                    consulta = ""
                },
                enabled = consulta.isNotBlank() && !viewModel.enviando
            ) {
                if (viewModel.enviando) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Enviar")
                }
            }
        }
    }
}

@Composable
private fun BurbujaMensaje(
    mensaje: ChatbotMensaje,
    onSeleccionarProducto: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mensaje.esUsuario) {
            Alignment.End
        } else {
            Alignment.Start
        }
    ) {
        Surface(
            color = if (mensaje.esUsuario) {
                Color(0xFF2563EB)
            } else {
                Color(0xFFEEF2F7)
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = mensaje.texto,
                    color = if (mensaje.esUsuario) {
                        Color.White
                    } else {
                        Color(0xFF1F2937)
                    },
                    fontSize = 14.sp
                )

                // HU 16 - Tarjetas de productos recomendados.
                mensaje.productos.forEach { producto ->
                    TarjetaProducto(
                        producto = producto,
                        onClick = {
                            producto.id?.let(onSeleccionarProducto)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaProducto(
    producto: ProductoResumenResponse,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFFE4E7EB)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = producto.nombre.orEmpty(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                text = "S/ ${producto.precio ?: "0.00"}",
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF166534),
                fontSize = 14.sp
            )

            producto.categoria?.let { categoria ->
                Text(
                    text = categoria,
                    fontSize = 12.sp,
                    color = Color(0xFF616E7C)
                )
            }

            Text(
                text = "Tocar para ver el detalle →",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
