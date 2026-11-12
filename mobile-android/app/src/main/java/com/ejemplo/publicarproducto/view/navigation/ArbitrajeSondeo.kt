package com.ejemplo.publicarproducto.view.navigation

/**
 * R1/R3 - Regla de arbitraje del sondeo de HU39 (`GET /api/conversaciones`).
 *
 * El sondeo de conversaciones tiene UN solo responsable en cada momento, y
 * además solo puede correr con la aplicación EN PRIMER PLANO:
 *
 * | Pestaña Consultas | App en primer plano | Quién sondea           |
 * |-------------------|----------------------|------------------------|
 * | activa            | sí                   | `ConsultasViewModel`   |
 * | activa            | no                   | nadie                  |
 * | otra              | sí                   | `AvisosConversacionViewModel` |
 * | otra              | no                   | nadie                  |
 *
 * Reglas:
 * - Con la pestaña Consultas activa, el sondeo lo hace el ViewModel de la
 *   pantalla (además refresca la conversación abierta): el ViewModel de
 *   avisos cede (`pausar()`).
 * - En el resto de pestañas, el sondeo lo hace el ViewModel de avisos, que
 *   alimenta el aviso de la barra superior.
 * - Con la aplicación en segundo plano, **no sondea nadie**: así no hay
 *   peticiones HTTP en background (R1).
 *
 * La regla vive aparte, como función pura, para poder verificarla en la JVM
 * sin levantar la interfaz.
 */
fun debeSonearAvisosDeConversacion(
    consultasActiva: Boolean,
    appEnPrimerPlano: Boolean
): Boolean = !consultasActiva && appEnPrimerPlano

/**
 * R1/M1 - ¿Es la pantalla de Consultas la responsable del sondeo?
 *
 * No basta con que la pestaña activa sea Consultas: cuando se abre una
 * subpantalla sobre ella (detalle de producto, edición, publicación…), la
 * pantalla de Consultas **sale de la composición** y su sondeo se detiene
 * (`LifecycleStartEffect` se descarta). Si el arbitraje no lo tuviera en
 * cuenta, en ese instante no sondearía nadie y el aviso de la barra superior
 * quedaría congelado hasta volver a la pestaña.
 *
 * La condición vive en una función pura sobre el estado real de navegación
 * para que la regresión sea detectable por una prueba: si se volviera a
 * mirar solo `pestana`, esta función devolvería `true` con una subpantalla
 * abierta y la prueba lo detectaría.
 */
fun consultasSonResponsable(estado: EstadoNavegacion): Boolean =
    estado.pestana == IndicesNavegacion.CONSULTAS && estado.enPantallaPrincipal

/**
 * R1/M2 - Qué hay que hacer con el sondeo al salir de la pantalla autenticada.
 */
enum class AccionAlSalir {
    /**
     * La sesión sigue abierta: la pantalla se va a recrear (rotación o
     * cambio de configuración). Solo se pausa el sondeo y **se conserva** la
     * memoria de los mensajes vistos, para no perder el aviso de la barra
     * superior ni los mensajes que lleguen durante la recreación.
     */
    PAUSAR,

    /** La sesión se ha cerrado: no queda nada que conservar. */
    DETENER
}

/**
 * R1/M2 - Decide qué hacer con el sondeo al salir de la pantalla autenticada.
 *
 * - Sesión abierta (`usuarioActivo = true`): la Activity se recreará, luego
 *   solo se pausa el sondeo y se conserva la memoria de los mensajes vistos.
 * - Sesión cerrada (`usuarioActivo = false`): se limpia todo, incluida esa
 *   memoria, porque ya no pertenece a nadie.
 */
fun accionAlSalirDeLaComposicion(usuarioActivo: Boolean): AccionAlSalir =
    if (usuarioActivo) AccionAlSalir.PAUSAR else AccionAlSalir.DETENER
