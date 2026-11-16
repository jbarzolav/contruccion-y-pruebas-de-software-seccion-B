package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.NotificacionResponse
import com.ejemplo.publicarproducto.model.PedidoResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * HU 45/46/47 - Pedidos propios del usuario.
 * HU 55 - Filtro y paginación de las compras del cliente autenticado.
 *
 * Mejora técnica Offline First: la lista se observa desde Room, así que se
 * puede consultar sin conexión y se actualiza sola cuando el backend
 * sincroniza. Las reseñas siguen requiriendo conexión (el endpoint actual no
 * admite reintento offline seguro).
 *
 * Grupo D-3:
 * - los avisos llegan de `NotificacionesRepositorio.observarAvisos` (ya no
 *   hay acceso directo a `notificacionDao()`);
 * - la edición de reseñas se delega en `PedidosRepositorio.editarResena`,
 *   que dentro coordina Retrofit → Room (el ViewModel ya no mezcla HTTP y
 *   base de datos);
 * - el estado de los filtros vive aquí y se aplica con [FiltrosPedidos].
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

    /** HU 59.7 - Pedido que se está cancelando (bloquea dobles envíos). */
    var cancelandoId by mutableStateOf<Long?>(null)
        private set
    var notificaciones by mutableStateOf<List<NotificacionResponse>>(emptyList())
        private set

    // ------------------------------------------------------------------
    // Filtros de la pantalla (borrador que escribe la interfaz).
    // ------------------------------------------------------------------
    var filtroEstado by mutableStateOf("")
    var filtroDesde by mutableStateOf("")
    var filtroHasta by mutableStateOf("")
    var filtroMontoMin by mutableStateOf("")
    var filtroMontoMax by mutableStateOf("")

    /** Filtros ya validados y en vigor sobre [pedidosFiltrados]. */
    var filtrosAplicados by mutableStateOf(FiltrosPedidos())
        private set

    /** Error de validación del último intento de aplicar filtros. */
    var errorFiltro by mutableStateOf<String?>(null)
        private set

    /** Compras que cumplen los filtros aplicados (misma lista si no hay). */
    val pedidosFiltrados: List<PedidoResponse>
        get() = filtrosAplicados.filtrar(pedidos)

    init {
        // R2 - La identidad del usuario se observa de forma reactiva: cuando
        // el usuario cambia se cancelan los collectors del anterior y se
        // limpian sus datos de presentación (ver `limpiarEstadoDeUsuario`).
        iniciarObservacionesDeUsuario()
    }

    /**
     * R2 - Observaciones de los datos del usuario activo.
     *
     * Los collectors viven en el alcance del usuario: al cambiar de cuenta se
     * cancelan y se crean de nuevo con el identificador nuevo.
     */
    override fun observarUsuario(usuarioId: Long, alcance: CoroutineScope) {
        // Copia local: la interfaz se actualiza sola con cada cambio de Room.
        alcance.launch {
            contenedor.pedidos.observarCompras(usuarioId).collect { pedidos = it }
        }
        alcance.launch {
            contenedor.notificaciones.observarAvisos(usuarioId).collect { avisos ->
                notificaciones = avisos
            }
        }
    }

    /** R2 - No se muestra nada del usuario anterior al cambiar de cuenta. */
    override fun limpiarEstadoDeUsuario() {
        pedidos = emptyList()
        notificaciones = emptyList()
        error = null
        mensaje = null
        cargando = false
        guardando = false
        actualizandoEstadoId = null
        cancelandoId = null
    }

    /** Valida el borrador y, solo si es válido, lo aplica a la lista. */
    fun aplicarFiltros() {
        val candidatos = FiltrosPedidos(
            estado = filtroEstado,
            desde = filtroDesde,
            hasta = filtroHasta,
            montoMin = filtroMontoMin,
            montoMax = filtroMontoMax
        )
        errorFiltro = candidatos.errorDeValidacion()
        if (errorFiltro == null) filtrosAplicados = candidatos
    }

    /** Limpia el borrador, los filtros aplicados y el error. */
    fun limpiarFiltros() {
        filtroEstado = ""
        filtroDesde = ""
        filtroHasta = ""
        filtroMontoMin = ""
        filtroMontoMax = ""
        filtrosAplicados = FiltrosPedidos()
        errorFiltro = null
    }

    /** Descarga los pedidos reales del backend y actualiza la copia local. */
    fun cargar() {
        // R2 - Se recuerda con qué usuario se lanzó la petición: si mientras
        // tanto cambia la sesión, el resultado NO se muestra en la pantalla
        // del usuario nuevo.
        val usuario = usuarioId
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.refrescarCompras(usuario)) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) sinConexion = false
                    is ResultadoApi.Error -> {
                        if (usuario != usuarioId) return@launch
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) error = e.message ?: "No se pudieron cargar tus pedidos."
            } finally {
                if (usuario == usuarioId) cargando = false
            }
        }
    }

    /** HU 45 - Edita el texto de una reseña propia (requiere conexión). */
    fun editarResena(idPedido: Long, idResena: Long, texto: String) {
        val normalizado = texto.trim()
        if (guardando || normalizado.isEmpty() || normalizado.length > 1000) return
        // R2 - La reseña se edita sobre el usuario que la solicitó; si la
        // sesión cambia, su resultado no se muestra en la pantalla del nuevo.
        val usuario = usuarioId
        guardando = true
        mensaje = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.editarResena(usuario, idPedido, idResena, normalizado)) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) mensaje = "Reseña actualizada correctamente."
                    is ResultadoApi.Error -> if (usuario == usuarioId) mensaje = resultado.mensaje
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) mensaje = e.message ?: "No se pudo actualizar la reseña."
            } finally {
                if (usuario == usuarioId) guardando = false
            }
        }
    }

    /** HU 47 - Consulta el estado vigente del pedido propio. */
    fun actualizarEstado(idPedido: Long) {
        if (actualizandoEstadoId != null) return
        // R2 - La consulta se lanza sobre el usuario activo en ese momento.
        val usuario = usuarioId
        actualizandoEstadoId = idPedido
        mensaje = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.refrescarEstadoPedido(usuario, idPedido)) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) mensaje = "Estado del pedido actualizado."
                    is ResultadoApi.Error -> {
                        if (usuario != usuarioId) return@launch
                        if (resultado.temporal) sinConexion = true
                        mensaje = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) mensaje = e.message ?: "No se pudo actualizar el estado del pedido."
            } finally {
                if (usuario == usuarioId) actualizandoEstadoId = null
            }
        }
    }

    /**
     * HU 59.7 - Cancela la compra propia mientras el backend la permita
     * (solo estado REGISTRADO).
     *
     * [cancelandoId] es la bandera anti-doble envío: mientras haya una
     * cancelación en vuelo cualquier otro intento se ignora, de modo que un
     * doble toque no dispara dos POST. El estado nuevo llega en la
     * respuesta y se aplica a la copia local (Room), así que la tarjeta
     * pasa a "Cancelado" sola. Cualquier error (400 si la compra ya salió
     * de REGISTRADO, 403 si es de otro cliente, 404, sin conexión) queda
     * visible en [mensaje].
     */
    fun cancelarPedido(idPedido: Long) {
        if (cancelandoId != null) return
        // R2 - La cancelación se lanza sobre el usuario activo en ese momento.
        val usuario = usuarioId
        cancelandoId = idPedido
        mensaje = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.cancelarPedido(usuario, idPedido)) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) mensaje = "Compra cancelada."
                    is ResultadoApi.Error -> {
                        if (usuario != usuarioId) return@launch
                        if (resultado.temporal) sinConexion = true
                        mensaje = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) mensaje = e.message ?: "No se pudo cancelar la compra."
            } finally {
                if (usuario == usuarioId) cancelandoId = null
            }
        }
    }
}
