package com.ejemplo.publicarproducto.domain

import com.ejemplo.publicarproducto.model.PedidoResponse
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Valida que una fecha de filtro tenga el formato AAAA-MM-DD real.
 *
 * Regla pura compartida por los filtros de pedidos (HU 46/HU 47) y por el
 * panel de métricas de ventas (HU 57). Vive en `domain` porque no depende de
 * Compose: la presentación la consume, pero la regla no pertenece a la View.
 */
internal fun fechaFiltroValida(fecha: String): Boolean = fecha.isBlank() ||
    runCatching { LocalDate.parse(fecha) }.isSuccess

/**
 * HU 46/HU 47/HU 55 - Filtros de "Mis pedidos".
 *
 * HU 55 - Filtra y pagina únicamente las compras del cliente autenticado: es
 * el mismo criterio que aplica el backend (`ExperienciaCompraService` y
 * `FiltroPedidos`) para mostrar solo las compras de la sesión.
 *
 * Lógica pura extraída EXACTAMENTE de `MisPedidosScreen` en el grupo D-3.
 * El predicado y la validación originales se copiaron sin cambios de
 * comportamiento; los casos que se conservan tal cual son:
 *
 * 1. `total` del pedido `null`: solo se excluye cuando hay filtro de precio
 *    (`pedido.total?.let { ... } == true` es `false`), no por el solo hecho
 *    de existir;
 * 2. `fecha` del pedido `null`: solo se excluye cuando hay filtro de fechas;
 * 3. mínimo igual al máximo: el rango es cerrado en ambos extremos;
 * 4. los montos se comparan con `BigDecimal.compareTo` (nunca Double), de
 *    modo que la escala no afecta (`120.00` == `120`) y la precisión se
 *    conserva (`10.0000000000000001` NO es `10`);
 * 5. las fechas y montos límite están INCLUIDOS (`>=` y `<=`);
 * 6. un campo en blanco no filtra nada;
 * 7. los filtros se combinan con Y y el orden de evaluación del `when` de
 *    validación no cambia (fechas → orden de fechas → montos numéricos →
 *    negativos → mínimo mayor que máximo).
 *
 * La fecha del pedido se recorta a sus primeros 10 caracteres
 * (`AAAA-MM-DD`) antes de comparar, igual que en la pantalla original.
 */
data class FiltrosPedidos(
    val estado: String = "",
    val desde: String = "",
    val hasta: String = "",
    val montoMin: String = "",
    val montoMax: String = ""
) {

    /** Monto mínimo tecleado: `null` cuando está vacío o no es numérico. */
    val minimo: BigDecimal?
        get() = montoMin.takeIf(String::isNotBlank)?.toBigDecimalOrNull()

    /** Monto máximo tecleado: `null` cuando está vacío o no es numérico. */
    val maximo: BigDecimal?
        get() = montoMax.takeIf(String::isNotBlank)?.toBigDecimalOrNull()

    /**
     * Validación del botón "Filtrar" (texto original de la pantalla).
     *
     * @return `null` cuando los filtros son válidos o el mensaje exacto que
     * ya mostraba la interfaz cuando no lo son.
     */
    fun errorDeValidacion(): String? {
        val minimo = this.minimo
        val maximo = this.maximo
        return when {
            !fechaFiltroValida(desde) || !fechaFiltroValida(hasta) ->
                "Usa fechas reales con formato AAAA-MM-DD."
            desde.isNotBlank() && hasta.isNotBlank() && desde > hasta ->
                "La fecha inicial no puede superar la fecha final."
            montoMin.isNotBlank() && minimo == null || montoMax.isNotBlank() && maximo == null ->
                "Ingresa montos válidos."
            (minimo != null && minimo.signum() < 0) || (maximo != null && maximo.signum() < 0) ->
                "Los montos no pueden ser negativos."
            minimo != null && maximo != null && minimo > maximo ->
                "El monto mínimo no puede superar al máximo."
            else -> null
        }
    }

    /**
     * Predicado original de `MisPedidosScreen`, aplicado a la lista completa
     * de compras. Conserva el orden de la lista y todos los casos de la
     * cabecera de este archivo.
     */
    fun filtrar(pedidos: List<PedidoResponse>): List<PedidoResponse> {
        val minimoAplicado = minimo
        val maximoAplicado = maximo
        return pedidos.filter { pedido ->
            val fecha = pedido.fecha?.take(10)
            (estado.isBlank() || pedido.estado.equals(estado, ignoreCase = true)) &&
                (desde.isBlank() || (fecha != null && fecha >= desde)) &&
                (hasta.isBlank() || (fecha != null && fecha <= hasta)) &&
                (minimoAplicado == null || pedido.total?.let { it.compareTo(minimoAplicado) >= 0 } == true) &&
                (maximoAplicado == null || pedido.total?.let { it.compareTo(maximoAplicado) <= 0 } == true)
        }
    }
}
