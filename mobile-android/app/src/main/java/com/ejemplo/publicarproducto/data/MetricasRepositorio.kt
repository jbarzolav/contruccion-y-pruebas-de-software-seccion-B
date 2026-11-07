package com.ejemplo.publicarproducto.data

import com.ejemplo.publicarproducto.local.EntidadMetricasVentas
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.MetricasVentasResponse
import com.ejemplo.publicarproducto.network.ProductoApi
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.network.ejecutarPeticion
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * HU 57 - Panel de métricas de ventas del vendedor (Offline First).
 *
 * La interfaz consume la copia local de Room. Solo se guarda el ÚLTIMO
 * resultado válido: la tabla se sobrescribe con cada respuesta real del
 * backend. Si la red falla, se conserva el último panel válido y la
 * interfaz informa que los datos son los de la última sincronización.
 */
class MetricasRepositorio(
    private val bd: PulgaTecDatabase,
    private val api: ProductoApi
) {

    private val gson = Gson()

    /** Último panel válido observado desde la copia local. */
    fun observar(usuarioId: Long): Flow<MetricasVentasResponse?> =
        bd.metricasDao().observar(usuarioId).map { entidad -> entidad?.let { leer(it.payload) } }

    /** Último panel válido guardado en el dispositivo (para lecturas puntuales). */
    suspend fun ultimoResultado(usuarioId: Long): MetricasVentasResponse? =
        bd.metricasDao().buscar(usuarioId)?.let { leer(it.payload) }

    /**
     * Descarga el panel real del backend y actualiza la copia local.
     * Solo se guarda cuando la respuesta es válida: un error nunca pisa el
     * último resultado correcto.
     */
    suspend fun refrescar(
        usuarioId: Long,
        desde: String?,
        hasta: String?,
        compararPeriodoAnterior: Boolean
    ): ResultadoApi<MetricasVentasResponse> {
        val resultado = ejecutarPeticion {
            api.obtenerMetricasVentas(desde, hasta, compararPeriodoAnterior)
        }
        if (resultado is ResultadoApi.Exito) {
            bd.metricasDao().guardar(
                EntidadMetricasVentas(
                    usuarioId = usuarioId,
                    clavePeriodo = clavePeriodo(desde, hasta, compararPeriodoAnterior),
                    payload = gson.toJson(resultado.valor),
                    fechaSincronizacion = System.currentTimeMillis()
                )
            )
        }
        return resultado
    }

    /** Borra el panel local del usuario (cierre de sesión). */
    suspend fun borrarUsuario(usuarioId: Long) = bd.metricasDao().borrarUsuario(usuarioId)

    private fun leer(payload: String): MetricasVentasResponse? =
        runCatching { gson.fromJson(payload, MetricasVentasResponse::class.java) }.getOrNull()

    companion object {
        /** Identifica el periodo al que pertenece el resultado guardado. */
        fun clavePeriodo(desde: String?, hasta: String?, comparar: Boolean): String =
            "$desde|$hasta|$comparar"
    }
}
