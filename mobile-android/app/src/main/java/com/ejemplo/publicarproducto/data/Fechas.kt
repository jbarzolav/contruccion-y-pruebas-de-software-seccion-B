package com.ejemplo.publicarproducto.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Normalización de fechas para la copia local.
 *
 * El backend devuelve `LocalDateTime` en ISO-8601 (por ejemplo
 * `2024-05-01T10:15:30` o `2024-05-01T10:15:30.123`). La base local compara
 * y ordena las fechas como texto, así que se guardan SIEMPRE con el mismo
 * formato fijo (`yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS`): de ese modo el orden
 * lexicográfico coincide con el orden real y los rangos de fechas replican
 * exactamente la semántica del backend (`desde` = inicio del día,
 * `hasta` = final del día).
 */
object Fechas {

    private val FORMATO_FIJO: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS")

    /** Normaliza una fecha del backend al formato fijo local. */
    fun normalizar(valor: String?): String? {
        if (valor.isNullOrBlank()) return valor
        return runCatching { LocalDateTime.parse(valor).format(FORMATO_FIJO) }.getOrDefault(valor)
    }

    /**
     * Límite inferior del rango: una fecha `AAAA-MM-DD` empieza a las 00:00:00
     * y una fecha con hora `AAAA-MM-DDTHH:MM` incluye ese minuto completo.
     */
    fun normalizarDesde(valor: String?): String? {
        if (valor.isNullOrBlank()) return null
        val base = runCatching { LocalDateTime.parse(valor) }.getOrNull() ?: return valor
        val inicio = if (valor.length <= 10) base.toLocalDate().atStartOfDay() else base
        return inicio.format(FORMATO_FIJO)
    }

    /**
     * Límite superior del rango: una fecha `AAAA-MM-DD` termina a las 23:59:59
     * y una fecha con hora `AAAA-MM-DDTHH:MM` incluye hasta el último
     * instante de ese minuto.
     */
    fun normalizarHasta(valor: String?): String? {
        if (valor.isNullOrBlank()) return null
        val base = runCatching { LocalDateTime.parse(valor) }.getOrNull() ?: return valor
        val fin = if (valor.length <= 10) {
            base.toLocalDate().atTime(23, 59, 59, 999_999_999)
        } else {
            base.withSecond(59).withNano(999_999_999)
        }
        return fin.format(FORMATO_FIJO)
    }

    /** Marca de tiempo local actual para las fechas de lectura generadas aquí. */
    fun ahora(): String = LocalDateTime.now().format(FORMATO_FIJO)
}
