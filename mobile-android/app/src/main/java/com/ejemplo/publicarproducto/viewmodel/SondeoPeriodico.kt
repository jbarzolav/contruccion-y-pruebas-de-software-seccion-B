package com.ejemplo.publicarproducto.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * R1/R3 - Sondeo periódico cancelable.
 *
 * Encapsula el único ciclo de sondeo de HU39. Garantiza tres cosas:
 *
 * 1. **Un solo trabajo activo**: llamar a [iniciar] dos veces no duplica el
 *    sondeo ni las peticiones HTTP.
 * 2. **Cancelación inmediata**: [detener] cancela el trabajo en curso, de
 *    modo que no quedan peticiones HTTP vivas al salir de la pantalla o al
 *    pasar a segundo plano.
 * 3. **La cancelación se propaga**: un `CancellationException` no se
 *    convierte en un error visible, y un fallo puntual de una iteración no
 *    detiene el sondeo.
 *
 * El intervalo se resuelve en cada iteración ([intervaloMs]) para que las
 * pruebas puedan acortarlo sin cambiar el contrato funcional de 15 segundos.
 */
internal class SondeoPeriodico(
    private val alcance: CoroutineScope,
    private val intervaloMs: () -> Long,
    private val bloque: suspend () -> Unit
) {

    private var trabajo: Job? = null

    /** `true` mientras el sondeo esté activo. */
    val activo: Boolean get() = trabajo?.isActive == true

    /** Arranca el sondeo. No duplica el trabajo si ya está activo. */
    fun iniciar() {
        if (trabajo?.isActive == true) return
        trabajo = alcance.launch {
            while (true) {
                delay(intervaloMs())
                try {
                    bloque()
                } catch (e: CancellationException) {
                    // La cancelación se propaga: no es un error del usuario.
                    throw e
                } catch (_: Exception) {
                    // Un fallo puntual de una iteración tampoco detiene el sondeo.
                }
            }
        }
    }

    /** Detiene el sondeo y cancela el trabajo en curso. */
    fun detener() {
        trabajo?.cancel()
        trabajo = null
    }
}
