
package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.repository.ChatbotRepositorio
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.ChatbotResponse
import com.ejemplo.publicarproducto.model.ProductoResumenResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * HU 16 - Recomendaciones de productos.
 * HU 17 - Consultas técnicas sobre componentes electrónicos.
 *
 * El ViewModel ya no habla con Retrofit ni con Gson: delega en
 * [ChatbotRepositorio] y solo conserva el estado de la pantalla y el
 * texto exacto que se pinta en la burbuja.
 */
enum class ModoChatbot {
    RECOMENDACIONES,
    CONSULTAS_TECNICAS
}

data class ChatbotMensaje(
    val esUsuario: Boolean,
    val texto: String,
    val productos: List<ProductoResumenResponse> = emptyList()
)

class ChatbotViewModel : ViewModel() {

    /** Repositorio del chatbot (se resuelve en cada llamada). */
    private val repositorio: ChatbotRepositorio
        get() = ProveedorDatos.actual.chatbot

    var modo by mutableStateOf(ModoChatbot.RECOMENDACIONES)
        private set

    var mensajes by mutableStateOf<List<ChatbotMensaje>>(
        listOf(
            ChatbotMensaje(
                esUsuario = false,
                texto = "¡Hola! 👋 Puedo recomendarte productos " +
                        "y responder consultas técnicas sobre componentes electrónicos."
            )
        )
    )
        private set

    var enviando by mutableStateOf(false)
        private set

    var mensajeError by mutableStateOf<String?>(null)
        private set

    fun cambiarModo(nuevoModo: ModoChatbot) {
        if (!enviando) {
            modo = nuevoModo
        }
    }

    /**
     * Envía la consulta al endpoint correspondiente.
     * HU 16: /api/chatbot/recomendaciones
     * HU 17: /api/chatbot/consultas-tecnicas
     */
    fun enviarConsulta(consulta: String) {
        val texto = consulta.trim()
        if (texto.isEmpty() || enviando) return

        val modoConsulta = modo

        enviando = true
        mensajeError = null

        mensajes = mensajes + ChatbotMensaje(
            esUsuario = true,
            texto = texto
        )

        viewModelScope.launch {
            try {
                when (val resultado = consultar(modoConsulta, texto)) {
                    is ResultadoApi.Exito -> agregarRespuesta(resultado.valor, modoConsulta)

                    is ResultadoApi.Error -> {
                        mensajeError = resultado.mensaje

                        mensajes = mensajes + ChatbotMensaje(
                            esUsuario = false,
                            texto = "⚠️ ${resultado.mensaje}"
                        )
                    }
                }
            } catch (e: CancellationException) {
                // Una cancelación no es un error de red: se propaga tal cual.
                throw e
            } catch (e: Exception) {
                // Red de seguridad: el repositorio ya traduce todas las
                // excepciones, pero se conserva el texto que mostraba la
                // interfaz por si algo inesperado escapa de él.
                val errorTexto = "${ChatbotRepositorio.SIN_CONEXION}${e.message}"

                mensajeError = errorTexto

                mensajes = mensajes + ChatbotMensaje(
                    esUsuario = false,
                    texto = "⚠️ $errorTexto"
                )
            } finally {
                enviando = false
            }
        }
    }

    /** Elige el endpoint según el modo activo (HU 16 / HU 17). */
    private suspend fun consultar(
        modoConsulta: ModoChatbot,
        texto: String
    ): ResultadoApi<ChatbotResponse> = when (modoConsulta) {
        ModoChatbot.RECOMENDACIONES -> repositorio.recomendarProductos(texto)
        ModoChatbot.CONSULTAS_TECNICAS -> repositorio.consultarComponenteTecnico(texto)
    }

    /** Pinta la respuesta del asistente (texto y tarjetas de producto). */
    private fun agregarRespuesta(cuerpo: ChatbotResponse, modoConsulta: ModoChatbot) {
        mensajes = mensajes + ChatbotMensaje(
            esUsuario = false,
            texto = cuerpo.mensaje
                ?: "No hay una respuesta disponible.",
            productos = if (
                modoConsulta == ModoChatbot.CONSULTAS_TECNICAS
            ) {
                emptyList()
            } else {
                cuerpo.productos ?: emptyList()
            }
        )
    }
}
