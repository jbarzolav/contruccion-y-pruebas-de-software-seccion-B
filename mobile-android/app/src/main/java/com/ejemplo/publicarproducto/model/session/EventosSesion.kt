package com.ejemplo.publicarproducto.model.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Eventos del ciclo de vida de la sesión (AUD-19). */
sealed interface EventoSesion {
    /** El usuario inició sesión o la sesión quedó validada. */
    data class SesionIniciada(val usuarioId: Long) : EventoSesion

    /** El usuario cerró sesión (HU 25). */
    data object SesionCerrada : EventoSesion

    /** El backend rechazó el Bearer (401): hay que volver al login. */
    data object SesionExpirada : EventoSesion
}

/** Bus compartido para los eventos del ciclo de vida de la sesión. */
object BusEventosSesion {
    private const val CAPACIDAD_EXTRA = 16
    private val fuente = MutableSharedFlow<EventoSesion>(
        replay = 1,
        extraBufferCapacity = CAPACIDAD_EXTRA
    )

    /** Flujo de eventos para los observadores. */
    val eventos: SharedFlow<EventoSesion> = fuente.asSharedFlow()

    /** Publica un evento sin suspender a quien lo emite. */
    fun publicar(evento: EventoSesion) {
        fuente.tryEmit(evento)
    }

    /** Limpia el replay entre pruebas JVM; no se usa en producción. */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun limpiarReplayParaPruebas() {
        fuente.resetReplayCache()
    }
}
