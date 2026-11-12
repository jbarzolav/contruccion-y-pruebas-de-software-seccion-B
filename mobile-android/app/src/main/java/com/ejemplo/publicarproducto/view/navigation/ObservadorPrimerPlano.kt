package com.ejemplo.publicarproducto.view.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * R1 - Estado de primer plano de la aplicación.
 *
 * Se apoya en el ciclo de vida del `Activity`:
 * - `ON_START` → la aplicación es visible;
 * - `ON_STOP`  → la aplicación está en segundo plano.
 *
 * Con la aplicación en segundo plano NO debe haber peticiones HTTP: por eso
 * el sondeo de conversaciones se detiene cuando este indicador pasa a
 * `false` (ver [debeSonearAvisosDeConversacion]).
 *
 * **El cambio se notifica de forma SÍNCRONA**, dentro del propio callback
 * del ciclo de vida, y no esperando a una recomposición. Compose no garantiza
 * recomponer después de `ON_STOP` (la recomposición puede quedar pausada o
 * estrangulada mientras el `Lifecycle` no esté en `STARTED`), de modo que una
 * decisión tomada en la composición podría no llegar a aplicarse nunca: el
 * sondeo seguiría activo con la aplicación oculta. Notificar desde el callback
 * es lo único que garantiza el contrato de AndroidX.
 *
 * Es una clase aparte, y no un bloque dentro del Composable, para que su
 * comportamiento se pueda verificar en las pruebas (JVM e instrumentadas) sin
 * depender de Compose.
 *
 * @param cicloVida ciclo de vida del `Activity` (o del `LifecycleOwner` local).
 * @param alCambiar se invoca en el mismo instante en que cambia el estado,
 *   con `true` si la aplicación pasa a primer plano y `false` si pasa a
 *   segundo plano.
 */
class ObservadorPrimerPlano(
    private val cicloVida: Lifecycle,
    private val alCambiar: (Boolean) -> Unit = {}
) {

    /**
     * `true` mientras la aplicación esté visible en pantalla.
     *
     * Es un `var` normal, **no** estado de Compose: la decisión no puede
     * depender de una recomposición, así que no tiene sentido (ni debe
     * tener) que Compose pueda observar este valor.
     */
    var enPrimerPlano: Boolean = cicloVida.currentState.isAtLeast(Lifecycle.State.STARTED)
        private set

    private val observador = LifecycleEventObserver { _, evento ->
        when (evento) {
            Lifecycle.Event.ON_START -> actualizar(true)
            Lifecycle.Event.ON_STOP -> actualizar(false)
            else -> Unit
        }
    }

    /**
     * Publica el nuevo estado y notifica el cambio **de forma síncrona**.
     *
     * Solo notifica cuando el valor cambia, para no repetir la decisión en
     * cada evento del ciclo de vida.
     */
    private fun actualizar(valor: Boolean) {
        val hayCambio = valor != enPrimerPlano
        enPrimerPlano = valor
        if (hayCambio) alCambiar(valor)
    }

    /**
     * Empieza a observar el ciclo de vida.
     *
     * Si el ciclo de vida ya está en STARTED (por ejemplo, al volver de
     * segundo plano sin recrear la pantalla), el indicador se sincroniza y se
     * notifica el estado inicial.
     */
    fun iniciar() {
        val valor = cicloVida.currentState.isAtLeast(Lifecycle.State.STARTED)
        enPrimerPlano = valor
        alCambiar(valor)
        cicloVida.addObserver(observador)
    }

    /** Deja de observar el ciclo de vida. */
    fun detener() {
        cicloVida.removeObserver(observador)
    }
}
