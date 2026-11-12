package com.ejemplo.publicarproducto.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Tope de espera de la cancelación del alcance (evita que la prueba se cuelgue). */
private const val ESPERA_CANCELACION_MS = 5_000L

/**
 * Cancela el alcance (`viewModelScope`) del ViewModel y espera a que termine.
 *
 * Lección D-3: los colectores de Room viven en el alcance del ViewModel. Si
 * la base se cierra mientras el colector sigue vivo, Room lanza
 * "Cannot perform this operation because the connection pool has been closed"
 * y el proceso de pruebas se cae. Por eso hay que cancelar el alcance ANTES
 * de cerrar la base y esperar a que la cancelación se complete.
 *
 * La espera tiene un tope ([ESPERA_CANCELACION_MS]): si algún hijo del
 * alcance no terminara, la prueba no se queda colgada para siempre y el
 * fallo se manifiesta al cerrar la base, con su diagnóstico propio.
 */
fun ViewModel.cancelarAlcance() {
    val job = viewModelScope.coroutineContext[Job]
    runCatching { viewModelScope.cancel() }
    if (job != null) {
        runBlocking {
            withTimeoutOrNull(ESPERA_CANCELACION_MS) { job.join() }
        }
    }
}
