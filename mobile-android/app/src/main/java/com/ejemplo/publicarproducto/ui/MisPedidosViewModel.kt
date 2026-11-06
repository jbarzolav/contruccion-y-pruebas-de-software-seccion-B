package com.ejemplo.publicarproducto.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.data.Mapeadores
import com.ejemplo.publicarproducto.model.NotificacionResponse
import com.ejemplo.publicarproducto.model.PedidoResponse
import com.ejemplo.publicarproducto.model.ResenaEdicionRequest
import com.ejemplo.publicarproducto.network.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * HU 46/50 - Pedidos propios del usuario.
 *
 * Mejora técnica Offline First: la lista se observa desde Room, así que se
 * puede consultar sin conexión y se actualiza sola cuando el backend
 * sincroniza. Las reseñas siguen requiriendo conexión (el endpoint actual no
 * admite reintento offline seguro).
 */
class MisPedidosViewModel : BaseOfflineViewModel() {

    var pedidos by mutableStateOf<List<PedidoResponse>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var guardando by mutableStateOf(false)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var actualizandoEstadoId by mutableStateOf<Long?>(null)
        private set
    var notificaciones by mutableStateOf<List<NotificacionResponse>>(emptyList())
        private set

    init {
        // Copia local: la interfaz se actualiza sola con cada cambio de Room.
        viewModelScope.launch {
            contenedor.pedidos.observarCompras(usuarioId).collect { pedidos = it }
        }
        viewModelScope.launch {
            contenedor.bd.notificacionDao().observarTodas(usuarioId).collect { avisos ->
                notificaciones = avisos.map { Mapeadores.aDto(it) }
            }
        }
    }

    /** Descarga los pedidos reales del backend y actualiza la copia local. */
    fun cargar() {
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.refrescarCompras(usuarioId)) {
                    is ResultadoApi.Exito -> sinConexion = false
                    is ResultadoApi.Error -> {
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudieron cargar tus pedidos."
            } finally {
                cargando = false
            }
        }
    }

    /** Refresca los avisos del usuario desde la copia local (sin red). */
    fun cargarNotificaciones() {
        // Los avisos ya se observan desde Room: no hace falta pedir nada.
    }

    /** HU 45 - Edita el texto de una reseña propia (requiere conexión). */
    fun editarResena(idPedido: Long, idResena: Long, texto: String) {
        val normalizado = texto.trim()
        if (guardando || normalizado.isEmpty() || normalizado.length > 1000) return
        guardando = true
        mensaje = null
        viewModelScope.launch {
            try {
                val respuesta = com.ejemplo.publicarproducto.network.UserApiClient.productoApi
                    .editarResena(idResena, ResenaEdicionRequest(normalizado))
                if (respuesta.isSuccessful && respuesta.body() != null) {
                    val actualizada = respuesta.body()!!
                    contenedor.pedidos.actualizarResenaLocal(usuarioId, idPedido, actualizada)
                    mensaje = "Reseña actualizada correctamente."
                } else {
                    mensaje = mensajeDeError(respuesta)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mensaje = e.message ?: "No se pudo actualizar la reseña."
            } finally {
                guardando = false
            }
        }
    }

    /** HU 47 - Consulta el estado vigente del pedido propio. */
    fun actualizarEstado(idPedido: Long) {
        if (actualizandoEstadoId != null) return
        actualizandoEstadoId = idPedido
        mensaje = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.refrescarEstadoPedido(usuarioId, idPedido)) {
                    is ResultadoApi.Exito -> mensaje = "Estado del pedido actualizado."
                    is ResultadoApi.Error -> {
                        if (resultado.temporal) sinConexion = true
                        mensaje = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mensaje = e.message ?: "No se pudo actualizar el estado del pedido."
            } finally {
                actualizandoEstadoId = null
            }
        }
    }

    private fun mensajeDeError(respuesta: retrofit2.Response<*>): String = try {
        val cuerpo = respuesta.errorBody()?.string()
        com.google.gson.Gson().fromJson(cuerpo, com.ejemplo.publicarproducto.model.ErrorResponse::class.java)
            ?.messages?.firstOrNull()
            ?: "Error ${respuesta.code()} al consultar pedidos."
    } catch (_: Exception) {
        "Error ${respuesta.code()} al consultar pedidos."
    }
}
