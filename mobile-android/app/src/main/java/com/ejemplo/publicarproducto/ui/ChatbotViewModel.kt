
package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.ChatbotRequest
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.ProductoResumenResponse
import com.ejemplo.publicarproducto.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * HU 16 - Recomendaciones de productos.
 * HU 17 - Consultas técnicas sobre componentes electrónicos.
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
                val request = ChatbotRequest(consulta = texto)

                val respuesta = when (modoConsulta) {
                    ModoChatbot.RECOMENDACIONES ->
                        RetrofitClient.productoApi.recomendarProductos(request)

                    ModoChatbot.CONSULTAS_TECNICAS ->
                        RetrofitClient.productoApi.consultarComponenteTecnico(request)
                }

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val cuerpo = respuesta.body()!!

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
                } else {
                    val errorTexto = mensajeDeError(respuesta)
                    mensajeError = errorTexto

                    mensajes = mensajes + ChatbotMensaje(
                        esUsuario = false,
                        texto = "⚠️ $errorTexto"
                    )
                }
            } catch (e: Exception) {
                val errorTexto =
                    "No hay conexión con el backend (10.0.2.2:8080): ${e.message}"

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

    private fun mensajeDeError(respuesta: Response<*>): String {
        return try {
            val cuerpo = respuesta.errorBody()?.string()
            val error = cuerpo?.let {
                Gson().fromJson(it, ErrorResponse::class.java)
            }

            error?.messages?.firstOrNull()
                ?: "Error ${respuesta.code()}: no se pudo consultar."
        } catch (e: Exception) {
            "Error ${respuesta.code()}: no se pudo consultar."
        }
    }
}
