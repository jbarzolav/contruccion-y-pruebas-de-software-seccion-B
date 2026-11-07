package com.ejemplo.publicarproducto.model

import java.math.BigDecimal

/**
 * HU 57 - Panel de métricas de ventas del vendedor.
 *
 * Réplica del DTO del backend. La identidad del vendedor NO viaja en la
 * petición: el backend la deriva de la sesión.
 *
 * - `periodoActual` siempre viene informado (con ceros si no hubo ventas).
 * - `periodoAnterior` y las comparativas llegan en null cuando no se pidió
 *   comparar: la interfaz no inventa datos.
 * - `variacionPorcentual` llega en null cuando el periodo anterior fue cero
 *   (no se puede calcular un porcentaje sobre una base inexistente).
 */
data class MetricasVentasResponse(
    val desde: String? = null,
    val hasta: String? = null,
    val desdeAnterior: String? = null,
    val hastaAnterior: String? = null,
    val compararPeriodoAnterior: Boolean? = null,
    val periodoActual: MetricaValoresResponse? = null,
    val periodoAnterior: MetricaValoresResponse? = null,
    val comparativaIngresos: ComparativaMetricaResponse? = null,
    val comparativaPedidos: ComparativaMetricaResponse? = null,
    val comparativaPromedio: ComparativaMetricaResponse? = null,
    val productosMasVendidos: List<ProductoVendidoResponse>? = null
)

/** Valores de una métrica en un periodo concreto. */
data class MetricaValoresResponse(
    val ingresosConfirmados: BigDecimal? = null,
    val cantidadPedidos: Long? = null,
    val promedioPorPedido: BigDecimal? = null
)

/** Comparación de una métrica entre el periodo actual y el anterior. */
data class ComparativaMetricaResponse(
    val diferenciaAbsoluta: BigDecimal? = null,
    val variacionPorcentual: BigDecimal? = null,
    val tendencia: String? = null
)

/** Producto destacado por unidades vendidas en el periodo. */
data class ProductoVendidoResponse(
    val idProducto: Long? = null,
    val nombre: String? = null,
    val imagenUrl: String? = null,
    val unidadesVendidas: Long? = null,
    val importeAtribuido: BigDecimal? = null
) {
    companion object {
        const val AUMENTO = "AUMENTO"
        const val DISMINUCION = "DISMINUCION"
        const val IGUAL = "IGUAL"
    }
}
