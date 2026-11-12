package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.local.Ambitos
import com.ejemplo.publicarproducto.domain.FiltrosVentas
import com.ejemplo.publicarproducto.model.NotificacionResponse
import com.ejemplo.publicarproducto.model.PedidoVendedorResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * HU 48 - Pedidos recibidos con solo los productos del vendedor.
 * HU 49 - Cambio de estado del pedido (el backend valida la transición).
 * HU 52 - Avisos de nueva compra: el backend avisa a cada vendedor con
 * productos en el pedido y el vendedor los ve en esta pantalla.
 * HU 56 - Filtro de los pedidos y los productos del vendedor autenticado.
 *
 * Mejora técnica Offline First: la lista se observa desde la copia local de
 * Room. El cambio de estado se guarda en el dispositivo y se encola en la
 * cola persistente: si no hay red se sincroniza al recuperar la conexión y,
 * si el servidor lo rechaza, se informa del rechazo sin affirmar que la
 * operación se confirmó.
 *
 * D-4: los avisos del vendedor también llegan del repositorio
 * (`NotificacionesRepositorio.observarAvisos`), que encapsula la consulta a
 * Room y el mapeo de las entidades locales a DTO. El ViewModel ya no accede
 * a ningún DAO ni a la base de datos.
 */
class PedidosRecibidosViewModel : BaseOfflineViewModel() {

    var estadoFiltro by mutableStateOf("")
        private set
    var desdeFiltro by mutableStateOf("")
        private set
    var hastaFiltro by mutableStateOf("")
        private set
    var productoIdFiltro by mutableStateOf("")
        private set
    private var filtrosAplicados by mutableStateOf(FiltrosVentas())
    var errorFiltro by mutableStateOf<String?>(null)
        private set
    val pedidosFiltrados: List<PedidoVendedorResponse>
        get() = filtrosAplicados.filtrar(pedidos)

    fun cambiarEstadoFiltro(valor: String) { estadoFiltro = valor }
    fun cambiarDesdeFiltro(valor: String) { desdeFiltro = valor }
    fun cambiarHastaFiltro(valor: String) { hastaFiltro = valor }
    fun cambiarProductoIdFiltro(valor: String) { productoIdFiltro = valor }

    fun aplicarFiltros() {
        val filtros = FiltrosVentas(estadoFiltro, desdeFiltro, hastaFiltro, productoIdFiltro)
        errorFiltro = filtros.errorDeValidacion()
        if (errorFiltro == null) filtrosAplicados = filtros
    }

    fun limpiarFiltros() {
        estadoFiltro = ""; desdeFiltro = ""; hastaFiltro = ""; productoIdFiltro = ""
        filtrosAplicados = FiltrosVentas()
        errorFiltro = null
    }

    var pedidos by mutableStateOf<List<PedidoVendedorResponse>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var mensaje by mutableStateOf<String?>(null)
        private set
    var guardandoId by mutableStateOf<Long?>(null)
        private set
    var notificaciones by mutableStateOf<List<NotificacionResponse>>(emptyList())
        private set
    var pedidoEnfocadoId by mutableStateOf<Long?>(null)
        private set

    /** Estado propuesto por el usuario que el servidor todavía no validó. */
    var estadosPendientes by mutableStateOf<Map<Long, String>>(emptyMap())
        private set

    init {
        // R2 - La identidad del usuario se observa de forma reactiva: cuando
        // el usuario cambia se cancelan los collectors del anterior y se
        // limpian sus datos de presentación.
        iniciarObservacionesDeUsuario()
    }

    /**
     * R2 - Observaciones de los datos del usuario activo.
     *
     * Los collectors viven en el alcance del usuario: al cambiar de cuenta se
     * cancelan y se crean de nuevo con el identificador nuevo.
     */
    override fun observarUsuario(usuarioId: Long, alcance: CoroutineScope) {
        alcance.launch {
            contenedor.pedidos.observarVentas(usuarioId).collect { ventas ->
                pedidos = ventas.pedidos
                estadosPendientes = ventas.estadosPendientes
            }
        }
        alcance.launch {
            // D-4: los avisos llegan del repositorio. `observarAvisos`
            // encapsula la consulta a Room y el mapeo a DTO, de modo que el
            // ViewModel ya no toca el DAO ni conoce las entidades locales.
            // La consulta, el orden y el mapeo son los mismos de siempre.
            recopilarAvisos(contenedor.notificaciones.observarAvisos(usuarioId)) { avisos ->
                notificaciones = avisos
            }
        }
    }

    /** R2 - No se muestra nada del usuario anterior al cambiar de cuenta. */
    override fun limpiarEstadoDeUsuario() {
        pedidos = emptyList()
        estadosPendientes = emptyMap()
        notificaciones = emptyList()
        pedidoEnfocadoId = null
        error = null
        mensaje = null
        cargando = false
        guardandoId = null
    }

    /** Descarga los pedidos recibidos reales del backend y actualiza Room. */
    fun cargar() {
        // R2 - Se recuerda con qué usuario se lanzó la petición: si mientras
        // tanto cambia la sesión, el resultado NO se muestra en la pantalla
        // del usuario nuevo.
        val usuario = usuarioId
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val resultado = contenedor.pedidos.refrescarVentas(usuario)) {
                    is ResultadoApi.Exito -> if (usuario == usuarioId) sinConexion = false
                    is ResultadoApi.Error -> {
                        if (usuario != usuarioId) return@launch
                        if (resultado.temporal) sinConexion = true else error = resultado.mensaje
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) error = e.message ?: "No se pudieron cargar los pedidos recibidos."
            } finally {
                if (usuario == usuarioId) cargando = false
            }
        }
    }

    fun enfocarPedido(idPedido: Long) {
        pedidoEnfocadoId = idPedido
    }

    /**
     * HU 49 - El vendedor propone el siguiente estado; el backend lo valida.
     *
     * La propuesta se guarda en el dispositivo y se encola. Hasta que el
     * servidor la confirme, la interfaz solo indica que está pendiente.
     */
    fun actualizarEstado(idPedido: Long, estado: String) {
        if (guardandoId != null || estado.isBlank()) return
        // R2 - La operación se lanza sobre el usuario activo en ese momento.
        val usuario = usuarioId
        guardandoId = idPedido
        error = null
        mensaje = null
        viewModelScope.launch {
            try {
                contenedor.pedidos.registrarEstadoPendienteLocal(
                    usuarioId = usuario,
                    idPedido = idPedido,
                    ambito = Ambitos.VENTAS,
                    estado = estado
                )
                contenedor.pedidos.encolarEstadoPedido(
                    usuarioId = usuario,
                    idPedido = idPedido,
                    ambito = Ambitos.VENTAS,
                    estado = estado
                )
                contenedor.programacionSincronizacion.programar(usuario)
                if (usuario == usuarioId) {
                    mensaje = "N.º de compra: $idPedido · Estado $estado guardado en el dispositivo. " +
                        "Pendiente de sincronizar con el servidor."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (usuario == usuarioId) error = e.message ?: "No se pudo actualizar el estado del pedido."
            } finally {
                if (usuario == usuarioId) guardandoId = null
            }
        }
    }
}

/**
 * Recoge los avisos del vendedor y los publica en el estado.
 *
 * Se deja como función aparte, y no como código dentro del `init`, para
 * poder verificar en la JVM el comportamiento observable de la observación
 * (orden conservado, reemisiones cuando la copia local cambia, errores y
 * cancelación) sin depender de Room, que solo existe en el dispositivo.
 *
 * En producción la fuente es `NotificacionesRepositorio.observarAvisos`,
 * que entrega la misma consulta, el mismo orden y el mismo mapeo que usaba
 * el ViewModel cuando consultaba el DAO directamente.
 */
internal suspend fun recopilarAvisos(
    fuente: Flow<List<NotificacionResponse>>,
    alActualizar: (List<NotificacionResponse>) -> Unit
) {
    fuente.collect { alActualizar(it) }
}
