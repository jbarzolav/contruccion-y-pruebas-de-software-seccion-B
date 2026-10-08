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

/** Mensaje del chatbot: del usuario o del bot (con sus productos sugeridos). */
data class ChatbotMensaje(
    val esUsuario: Boolean,
    val texto: String,
    val productos: List<ProductoResumenResponse> = emptyList()
)

/**
 * HU 16 - Chatbot de recomendaciones: envía la consulta al backend
 * (POST /api/chatbot/recomendaciones) y guarda el mensaje de respuesta
 * con las tarjetas de productos sugeridos (solo disponibles).
 */
class ChatbotViewModel : ViewModel() {

    var mensajes by mutableStateOf<List<ChatbotMensaje>>(
        listOf(
            ChatbotMensaje(
                esUsuario = false,
                texto = "¡Hola! 👋 Pregúntame por productos, por ejemplo: " +
                        "\"notebook\", \"mouse\" o \"Computadoras\"."
            )
        )
    )
        private set

    var enviando by mutableStateOf(false)
        private set

    var mensajeError by mutableStateOf<String?>(null)
        private set

    /** HU 16 - POST /api/chatbot/recomendaciones con la consulta del usuario. */
    fun enviarConsulta(consulta: String) {
        val texto = consulta.trim()
        if (texto.isEmpty() || enviando) return

        enviando = true
        mensajeError = null
        mensajes = mensajes + ChatbotMensaje(esUsuario = true, texto = texto)

        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.productoApi.recomendarProductos(
                    ChatbotRequest(consulta = texto)
                )

                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val cuerpo = respuesta.body()!!
                    mensajes = mensajes + ChatbotMensaje(
                        esUsuario = false,
                        texto = cuerpo.mensaje ?: "No hay sugerencias disponibles.",
                        productos = cuerpo.productos ?: emptyList()
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

    /** Lee {status, error, messages} del backend para mostrar la regla violada. */
    private fun mensajeDeError(respuesta: Response<*>): String {
        return try {
            val cuerpo = respuesta.errorBody()?.string()
            val error = cuerpo?.let { Gson().fromJson(it, ErrorResponse::class.java) }
            error?.messages?.firstOrNull() ?: "Error ${respuesta.code()}: no se pudo consultar."
        } catch (e: Exception) {
            "Error ${respuesta.code()}: no se pudo consultar."
        }
    }
}
