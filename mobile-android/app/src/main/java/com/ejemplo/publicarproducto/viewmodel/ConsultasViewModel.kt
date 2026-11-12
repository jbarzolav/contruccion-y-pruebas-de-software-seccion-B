package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.repository.ConversacionesRepositorio
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.remote.ConversacionResumenResponse
import com.ejemplo.publicarproducto.model.remote.MensajeConsulta
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * HU 38 - Consultas y conversaciones.
 * HU 39 - Comunicación y respuestas.
 *
 * El ViewModel ya no habla con Retrofit ni con Gson: delega en
 * [ConversacionesRepositorio] y conserva únicamente el estado de la
 * pantalla, la selección de la conversación y la paginación.
 *
 * R1/R3 - El ViewModel es el ÚNICO responsable del sondeo de HU39.
 *
 * Antes coexistían dos sondeos de `GET /api/conversaciones`: el `LaunchedEffect`
 * de la pantalla y el del ViewModel de avisos. Ahora el sondeo vive aquí, con
 * su intervalo funcional de 15 segundos, y la pantalla solo lo activa y lo
 * desactiva según su ciclo de vida (`LifecycleStartEffect`): no se generan
 * peticiones HTTP en segundo plano ni se duplican las llamadas.
 */
class ConsultasViewModel(
    /**
     * Repositorio de conversaciones.
     *
     * Se inyecta para que las pruebas de JVM puedan comprobar el ciclo del
     * sondeo (R1/R3) contra un servidor de pruebas local, sin depender de
     * Room ni del contenedor de datos. En producción resuelve el repositorio
     * real de `ProveedorDatos`.
     */
    private val repositorio: ConversacionesRepositorio = ProveedorDatos.actual.conversaciones
) : ViewModel() {
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

    /**
     * Conversación abierta en la pantalla.
     *
     * Vive en el ViewModel (y no en la Compose) porque el sondeo necesita
     * saber qué conversación hay que refrescar.
     */
    var conversacionSeleccionada by mutableStateOf<Long?>(null)
        private set

    /** Repositorio de conversaciones (se resuelve en cada llamada). */
    private var actualizandoSilencioso = false

    /**
     * Sondeo de HU39 (R1/R3).
     *
     * Es el ÚNICO trabajo de sondeo de la pantalla: `iniciarSondeo` no lo
     * duplica y `detenerSondeo` lo cancela de inmediato.
     */
    private val sondeo = SondeoPeriodico(
        alcance = viewModelScope,
        intervaloMs = { intervaloSondeoMs },
        bloque = { refrescarPeriodicamente(conversacionSeleccionada) }
    )

    /**
     * Intervalo del sondeo de HU39.
     *
     * Son 15 segundos, el intervalo funcional vigente. Solo las pruebas lo
     * acortan para poder verificar el ciclo sin esperar.
     */
    internal var intervaloSondeoMs: Long = INTERVALO_SONDEO_MS

    /** Cierra la conversación abierta (la pantalla la usa para deseleccionar). */
    fun cerrarConversacion() {
        conversacionSeleccionada = null
        mensajes = emptyList()
        paginaMensajes = 0
        tieneMensajesAnteriores = false
    }

    /**
     * HU 38/HU 39 - Abre una conversación y carga su historial.
     *
     * La selección vive en el ViewModel para que el sondeo pueda refrescar
     * la conversación abierta sin consultar a la Compose.
     */
    fun seleccionarConversacion(idConversacion: Long?) {
        conversacionSeleccionada = idConversacion
        texto = ""
        if (idConversacion != null) cargarMensajes(idConversacion)
    }

    /**
     * R1/R3 - Inicia el sondeo de HU39 (lista de conversaciones y
     * conversación abierta).
     *
     * Solo hay un sondeo activo: las llamadas posteriores no lo duplican. La
     * pantalla lo invoca cuando está STARTED y lo detiene al salir o al pasar
     * a segundo plano.
     */
    fun iniciarSondeo() {
        sondeo.iniciar()
    }

    /**
     * R1/R3 - Detiene el sondeo de HU39.
     *
     * La cancelación del trabajo no se muestra como error: el `finally` del
     * refresco deja el estado como estaba.
     */
    fun detenerSondeo() {
        sondeo.detener()
    }

    /** El sondeo está activo (lo usan las pruebas y la auditoría). */
    internal fun sondeoActivo(): Boolean = sondeo.activo

    fun cargar(listar: Boolean) {
        if (cargando) return
        cargando = true
        error = null
        viewModelScope.launch {
            try {
                when (val configuracion = repositorio.configuracion()) {
                    is ResultadoApi.Error -> error = configuracion.mensaje

                    is ResultadoApi.Exito -> {
                        maximo = configuracion.valor.longitudMaxima
                        if (listar) {
                            when (val lista = repositorio.listarConversaciones()) {
                                is ResultadoApi.Error -> error = lista.mensaje
                                is ResultadoApi.Exito -> conversaciones = lista.valor
                            }
                        }
                    }
                }
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
                when (val resultado = repositorio.obtenerMensajes(id, pagina)) {
                    is ResultadoApi.Error -> errorMensajes = resultado.mensaje

                    is ResultadoApi.Exito -> {
                        // La página llega ya en orden cronológico (el repositorio
                        // invierte el orden descendente del backend UNA sola vez).
                        mensajes = repositorio.acumularMensajes(
                            pagina = pagina,
                            mensajesPagina = resultado.valor.mensajes,
                            actuales = mensajes
                        )
                        paginaMensajes = resultado.valor.pagina
                        tieneMensajesAnteriores = resultado.valor.tieneSiguiente
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { errorMensajes = e.message ?: "No se pudo cargar el historial."
            } finally { cargandoMensajes = false }
        }
    }

    /**
     * HU39 - Mantiene al día la lista y la conversación abierta sin interrumpir
     * la lectura.
     *
     * No cambia los errores visibles: un fallo puntual del refresco periódico
     * no debe sustituir el aviso que el usuario está viendo.
     */
    fun actualizarSilencioso(conversacionSeleccionada: Long?) {
        if (!puedeRefrescar()) return
        actualizandoSilencioso = true
        viewModelScope.launch {
            try {
                refrescar(conversacionSeleccionada)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // El refresco periódico no debe reemplazar los errores de carga visibles.
            } finally { actualizandoSilencioso = false }
        }
    }

    /** El sondeo periódico aplica la misma política que el refresco manual. */
    private suspend fun refrescarPeriodicamente(conversacionSeleccionada: Long?) {
        if (!puedeRefrescar()) return
        actualizandoSilencioso = true
        try {
            refrescar(conversacionSeleccionada)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Un fallo puntual de una iteración tampoco detiene el sondeo.
        } finally { actualizandoSilencioso = false }
    }

    /** No se refresca mientras hay otra operación en curso. */
    private fun puedeRefrescar(): Boolean =
        !actualizandoSilencioso && !cargando && !cargandoMensajes && !enviando

    /** Trabajo real del refresco silencioso. */
    private suspend fun refrescar(conversacionSeleccionada: Long?) {
        when (val lista = repositorio.listarConversaciones()) {
            is ResultadoApi.Exito -> conversaciones = lista.valor
            is ResultadoApi.Error -> {
                // El refresco periódico no debe reemplazar los errores visibles.
            }
        }
        if (conversacionSeleccionada != null) {
            when (val pagina = repositorio.obtenerMensajes(conversacionSeleccionada, 0)) {
                is ResultadoApi.Exito -> {
                    mensajes = repositorio.acumularMensajes(
                        pagina = 0,
                        mensajesPagina = pagina.valor.mensajes,
                        actuales = mensajes
                    )
                    paginaMensajes = pagina.valor.pagina
                    tieneMensajesAnteriores = pagina.valor.tieneSiguiente
                }

                is ResultadoApi.Error -> {
                    // El refresco periódico no debe reemplazar los errores visibles.
                }
            }
        }
    }

    /**
     * Envía un mensaje de la conversación.
     *
     * - HU 36 - `productoId` no nulo: el cliente abre una consulta nueva
     *   sobre un producto (`POST /api/conversaciones`).
     * - HU 37 - `conversacionId` no nulo: el vendedor responde a una
     *   consulta recibida (`POST /api/conversaciones/{id}/mensajes`).
     */
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
                val resultado = if (productoId != null) {
                    repositorio.consultarSobreProducto(productoId, mensaje)
                } else {
                    repositorio.responderConsulta(requireNotNull(conversacionId), mensaje)
                }
                when (resultado) {
                    is ResultadoApi.Error -> error = resultado.mensaje

                    is ResultadoApi.Exito -> {
                        texto = ""
                        exito = "Mensaje enviado correctamente."
                        if (conversacionId != null) {
                            cargar(true)
                            cargarMensajes(conversacionId)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "No se pudo enviar el mensaje."
            } finally { enviando = false }
        }
    }

    override fun onCleared() {
        // R1 - El sondeo se cancela con el ViewModel: nunca queda un trabajo
        // de red vivo después de salir de la pantalla.
        detenerSondeo()
        super.onCleared()
    }

    internal companion object {
        /** Intervalo funcional del sondeo de HU39 (15 segundos). */
        const val INTERVALO_SONDEO_MS = 15_000L
    }
}
