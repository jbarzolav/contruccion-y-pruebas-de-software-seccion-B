package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.network.*
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response

class ConsultasViewModel : ViewModel() {
    var conversaciones by mutableStateOf<List<ConversacionResumenResponse>>(emptyList()); private set
    var mensajes by mutableStateOf<List<MensajeConsulta>>(emptyList()); private set
    var paginaMensajes by mutableStateOf(0); private set
    var tieneMensajesAnteriores by mutableStateOf(false); private set
    var cargandoMensajes by mutableStateOf(false); private set
    var errorMensajes by mutableStateOf<String?>(null); private set
    var cargando by mutableStateOf(false); private set
    var enviando by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var exito by mutableStateOf<String?>(null); private set
    var maximo by mutableStateOf<Int?>(null); private set
    var texto by mutableStateOf("")
    private val api get() = UserApiClient.conversacionApi
    private var actualizandoSilencioso = false

    fun cargar(listar: Boolean) {
        if (cargando) return
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                maximo = exigir(api.configuracion()).longitudMaxima
                if (listar) conversaciones = exigir(api.listar())
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "No se pudieron cargar las consultas."
            } finally { cargando = false }
        }
    }
    fun cargarMensajes(id: Long, pagina: Int = 0) {
        if (cargandoMensajes) return
        cargandoMensajes = true
        errorMensajes = null
        viewModelScope.launch {
            try {
                val resultado = exigir(api.mensajes(id, pagina))
                val cronologicos = resultado.mensajes.reversed()
                mensajes = if (pagina == 0) cronologicos else cronologicos + mensajes
                paginaMensajes = resultado.pagina
                tieneMensajesAnteriores = resultado.tieneSiguiente
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { errorMensajes = e.message ?: "No se pudo cargar el historial."
            } finally { cargandoMensajes = false }
        }
    }

    /** HU39 - Mantiene al día la lista y la conversación abierta sin interrumpir la lectura. */
    fun actualizarSilencioso(conversacionSeleccionada: Long?) {
        if (actualizandoSilencioso || cargando || cargandoMensajes || enviando) return
        actualizandoSilencioso = true
        viewModelScope.launch {
            try {
                conversaciones = exigir(api.listar())
                if (conversacionSeleccionada != null) {
                    val pagina = exigir(api.mensajes(conversacionSeleccionada, 0))
                    mensajes = pagina.mensajes.reversed()
                    paginaMensajes = pagina.pagina
                    tieneMensajesAnteriores = pagina.tieneSiguiente
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                // El refresco periódico no debe reemplazar los errores de carga visibles.
            } finally { actualizandoSilencioso = false }
        }
    }

    fun enviar(productoId: Long?, conversacionId: Long?) {
        if (enviando) return
        val limite = maximo ?: return
        val mensaje = texto.trim()
        exito = null
        if (mensaje.isBlank() || mensaje.length > limite) { error = "Escribe entre 1 y $limite caracteres."; return }
        enviando = true
        error = null
        viewModelScope.launch {
            try {
                if (productoId != null) exigir(api.consultar(ConsultaRequest(productoId, mensaje)))
                else exigir(api.responder(requireNotNull(conversacionId), RespuestaConsultaRequest(mensaje)))
                texto = ""
                exito = "Mensaje enviado correctamente."
                if (conversacionId != null) {
                    cargar(true)
                    cargarMensajes(conversacionId)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "No se pudo enviar el mensaje."
            } finally { enviando = false }
        }
    }
    private fun <T> exigir(respuesta: Response<T>): T {
        if (respuesta.isSuccessful) return requireNotNull(respuesta.body())
        val mensaje = runCatching { Gson().fromJson(respuesta.errorBody()?.string(), ErrorResponse::class.java)?.messages?.firstOrNull() }.getOrNull()
        throw IllegalStateException(mensaje ?: "Error ${respuesta.code()}. Intenta nuevamente.")
    }
}
