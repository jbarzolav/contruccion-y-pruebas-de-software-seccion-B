package com.ejemplo.publicarproducto.view.navigation

/**
 * R1/R3 - Aplica la regla de arbitraje del sondeo de `GET /api/conversaciones`.
 *
 * La regla [debeSonearAvisosDeConversacion] decide **quién** sondea; este
 * coordinador mantiene el estado necesario para decidir y **aplica** la
 * decisión:
 *
 * - `consultasActivasAhora` y `usuarioActivoAhora` se consultan **en el
 *   momento** de decidir, no se guarda una copia. Así, al volver al primer
 *   plano se decide con la pantalla y la sesión que haya en ese instante,
 *   sin depender de que Compose llegue a recomponer (y sin quedar desfasado
 *   si el cambio de pantalla y el paso a segundo plano se producen en el
 *   mismo instante).
 * - `enPrimerPlano` lo mantiene [ObservadorPrimerPlano], que notifica **de
 *   forma síncrona** desde el callback del ciclo de vida.
 *
 * La aplicación de la decisión NO depende de ninguna recomposición: en el
 * `ON_STOP` el sondeo se cancela dentro del propio callback, que es lo
 * único que garantiza AndroidX cuando la aplicación pasa a segundo plano.
 *
 * @param usuarioActivoAhora identidad del usuario con sesión **en el momento
 *   de decidir**. Se consulta en vivo (no se guarda una copia) para que, si
 *   la sesión caduca mientras la aplicación está en segundo plano, no se
 *   reanude el sondeo con una identidad obsoleta.
 * @param consultasActivasAhora `true` si la pantalla de Consultas está
 *   realmente en pantalla (y por tanto es la responsable del sondeo).
 * @param alIniciarSondeo arranca el sondeo del ViewModel de avisos.
 * @param alPausarSondeo cancela el sondeo del ViewModel de avisos.
 */
class CoordinadorSondeoAvisos(
    private val usuarioActivoAhora: () -> Long?,
    private val consultasActivasAhora: () -> Boolean,
    private val alIniciarSondeo: (usuarioId: Long) -> Unit,
    private val alPausarSondeo: () -> Unit
) {

    /** `true` mientras la aplicación esté visible en pantalla. */
    private var enPrimerPlano = false

    /**
     * Vuelve a aplicar la decisión con el estado actual.
     *
     * Se llama desde la navegación cuando cambia la pantalla visible o la
     * identidad del usuario.
     */
    fun reevaluar() {
        aplicarDecision()
    }

    /**
     * Avisa de un cambio de primer plano.
     *
     * Se invoca desde el callback del ciclo de vida (`ON_START` / `ON_STOP`),
     * de forma **síncrona**: en `ON_STOP` el sondeo se cancela en ese mismo
     * instante, sin esperar a ninguna recomposición.
     */
    fun onCambioPrimerPlano(enPrimerPlano: Boolean) {
        this.enPrimerPlano = enPrimerPlano
        aplicarDecision()
    }

    /**
     * Aplica la regla de arbitraje con el estado actual.
     *
     * Si no hay usuario con sesión no hay nada que sondear: el sondeo solo
     * existe dentro de una sesión autenticada.
     */
    private fun aplicarDecision() {
        val usuario = usuarioActivoAhora()
        val debeSonear = usuario != null && debeSonearAvisosDeConversacion(
            consultasActiva = consultasActivasAhora(),
            appEnPrimerPlano = enPrimerPlano
        )
        if (debeSonear) alIniciarSondeo(usuario) else alPausarSondeo()
    }
}
