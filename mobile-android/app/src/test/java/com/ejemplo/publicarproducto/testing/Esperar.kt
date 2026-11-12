package com.ejemplo.publicarproducto.testing

import kotlinx.coroutines.delay
import org.junit.Assert.fail

/** Tiempo máximo de espera por defecto en las pruebas de la JVM. */
const val ESPERA_POR_DEFECTO_MS: Long = 5_000L

/**
 * Espera a que se cumpla una condición.
 *
 * Falla explícitamente cuando se agota el tiempo: el error dice qué se
 * estaba esperando, cuánto se esperó y desde qué línea se pidió la espera,
 * en lugar de dejar que la aserción siguiente falle con un mensaje que no
 * explica la causa real.
 *
 * @param descripcion texto que explica qué se está esperando. Aparece en el
 *   mensaje de fallo.
 * @param timeoutMs tiempo máximo de espera.
 * @param condicion se evalúa repetidamente hasta que devuelva `true`.
 */
suspend fun esperarCondicion(
    descripcion: String,
    timeoutMs: Long = ESPERA_POR_DEFECTO_MS,
    condicion: () -> Boolean
) {
    val limite = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < limite) {
        if (condicion()) return
        delay(20)
    }
    fail(
        "Timeout de ${timeoutMs} ms esperando: $descripcion. " +
            "La condición no se cumplió dentro del tiempo previsto. " +
            "Espera pedida en: ${origenDeLaEspera()}"
    )
}

/**
 * Localiza el archivo y la línea desde la que se pidió la espera.
 *
 * Se saltan los marcos del propio helper (`esperarCondicion`) y de los
 * envoltorios locales `esperar`, para señalar al código de la prueba.
 */
private fun origenDeLaEspera(): String {
    val marco = Throwable().stackTrace.firstOrNull {
        !it.className.contains("EsperarKt") &&
            it.methodName != "esperarCondicion" &&
            it.methodName != "esperar"
    } ?: return "desconocido"
    return "${marco.fileName}:${marco.lineNumber} (${marco.className}.${marco.methodName})"
}
