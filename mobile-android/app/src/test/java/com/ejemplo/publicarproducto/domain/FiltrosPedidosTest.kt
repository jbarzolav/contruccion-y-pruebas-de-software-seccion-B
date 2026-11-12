package com.ejemplo.publicarproducto.domain

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import com.ejemplo.publicarproducto.model.PedidoResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Grupo D-3 - FiltrosPedidos (HU 46 y HU 47).
 *
 * Pruebas unitarias (JVM, sin emulador) de la lógica pura extraída de
 * `MisPedidosScreen`. Cada caso reproduce el comportamiento ORIGINAL de la
 * pantalla: valores nulos, límites inclusivos, `BigDecimal` sin Double,
 * combinación de filtros y el orden exacto de la validación.
 */
class FiltrosPedidosTest {

    private fun pedido(
        id: Long,
        fecha: String? = null,
        total: BigDecimal? = null,
        estado: String? = null
    ): PedidoResponse = PedidoResponse(
        idPedido = id,
        clienteId = 1L,
        fecha = fecha,
        total = total,
        detalles = emptyList(),
        estado = estado
    )

    private val pedidos = listOf(
        pedido(1, fecha = "2024-05-01T10:15:30", total = "120.00".toBigDecimal(), estado = "REGISTRADO"),
        pedido(2, fecha = "2024-06-15T09:00:00", total = "80.50".toBigDecimal(), estado = "ENTREGADO"),
        pedido(3, fecha = "2024-07-20T18:30:00", total = "200.00".toBigDecimal(), estado = "CANCELADO")
    )

    // ------------------------------------------------------------ sin filtros
    @Test
    fun sinFiltrosDevuelveTodosLosPedidos() {
        assertEquals(listOf(1L, 2L, 3L), FiltrosPedidos().filtrar(pedidos).map { it.idPedido })
    }

    // --------------------------------------------------------------- estado
    @Test
    fun elFiltroPorEstadoIgnoraMayusculasYMinusculas() {
        val filtrados = FiltrosPedidos(estado = "entregado").filtrar(pedidos)
        assertEquals(listOf(2L), filtrados.map { it.idPedido })
    }

    @Test
    fun elFiltroPorEstadoSinCoincidenciasDevuelveVacio() {
        assertTrue(FiltrosPedidos(estado = "EN_PROCESO").filtrar(pedidos).isEmpty())
    }

    @Test
    fun unPedidoSinEstadoSoloPasaSinFiltroDeEstado() {
        val lista = listOf(pedido(9, fecha = "2024-05-01T10:00:00", total = "10.00".toBigDecimal(), estado = null))

        assertEquals(1, FiltrosPedidos().filtrar(lista).size)
        assertTrue(FiltrosPedidos(estado = "REGISTRADO").filtrar(lista).isEmpty())
    }

    // --------------------------------------------------------------- fechas
    @Test
    fun laFechaLimiteInferiorEstaIncluida() {
        val filtrados = FiltrosPedidos(desde = "2024-05-01").filtrar(pedidos)
        assertEquals(listOf(1L, 2L, 3L), filtrados.map { it.idPedido })
    }

    @Test
    fun laFechaLimiteSuperiorEstaIncluida() {
        val filtrados = FiltrosPedidos(hasta = "2024-06-15").filtrar(pedidos)
        assertEquals(listOf(1L, 2L), filtrados.map { it.idPedido })
    }

    @Test
    fun soloDesdeNoLimitaPorArriba() {
        val filtrados = FiltrosPedidos(desde = "2024-06-01").filtrar(pedidos)
        assertEquals(listOf(2L, 3L), filtrados.map { it.idPedido })
    }

    @Test
    fun laFechaDelPedidoSeRecortaAPrimerosDiezCaracteres() {
        val filtrados = FiltrosPedidos(desde = "2024-05-01", hasta = "2024-05-01").filtrar(pedidos)
        // El pedido guarda "2024-05-01T10:15:30": la comparación usa "2024-05-01".
        assertEquals(listOf(1L), filtrados.map { it.idPedido })
    }

    @Test
    fun unaFechaNulaSeExcluyeSoloCuandoHayFiltroDeFecha() {
        val conFechaNula = listOf(pedido(9, fecha = null, total = "10.00".toBigDecimal(), estado = "REGISTRADO"))

        assertEquals(1, FiltrosPedidos().filtrar(conFechaNula).size)
        assertTrue(FiltrosPedidos(desde = "2024-01-01").filtrar(conFechaNula).isEmpty())
        assertTrue(FiltrosPedidos(hasta = "2030-01-01").filtrar(conFechaNula).isEmpty())
    }

    // --------------------------------------------------------------- precios
    @Test
    fun unTotalNuloSeExcluyeSoloCuandoHayFiltroDePrecio() {
        val conTotalNulo = listOf(pedido(9, fecha = "2024-05-01T10:00:00", total = null, estado = "REGISTRADO"))

        assertEquals(1, FiltrosPedidos().filtrar(conTotalNulo).size)
        assertEquals(1, FiltrosPedidos(estado = "REGISTRADO").filtrar(conTotalNulo).size)
        assertTrue(FiltrosPedidos(montoMin = "1").filtrar(conTotalNulo).isEmpty())
        assertTrue(FiltrosPedidos(montoMax = "999").filtrar(conTotalNulo).isEmpty())
    }

    @Test
    fun minimoIgualMaximoFormaUnRangoCerrado() {
        val exacto = FiltrosPedidos(montoMin = "120.00", montoMax = "120.00").filtrar(pedidos)
        assertEquals(listOf(1L), exacto.map { it.idPedido })

        assertTrue(FiltrosPedidos(montoMin = "130", montoMax = "130").filtrar(pedidos).isEmpty())
    }

    @Test
    fun losMontosLimiteEstanIncluidosEnAmbosExtremos() {
        val minimo = FiltrosPedidos(montoMin = "80.50").filtrar(pedidos)
        assertEquals(listOf(1L, 2L, 3L), minimo.map { it.idPedido })

        val maximo = FiltrosPedidos(montoMax = "80.50").filtrar(pedidos)
        assertEquals(listOf(2L), maximo.map { it.idPedido })
    }

    @Test
    fun laComparacionDePreciosUsaBigDecimalSinPerderPrecision() {
        // Con Double, 10.0000000000000001 se redondea a 10 y entraría en el
        // rango; con BigDecimal.compareTo (como en la pantalla) no entra.
        val preciso = listOf(pedido(1, total = "10.0000000000000001".toBigDecimal()))
        assertTrue(FiltrosPedidos(montoMax = "10").filtrar(preciso).isEmpty())

        // La escala no cuenta: 120.00 == 120 con compareTo (no con equals).
        val conEscala = listOf(pedido(2, total = "120.00".toBigDecimal()))
        assertEquals(1, FiltrosPedidos(montoMin = "120", montoMax = "120").filtrar(conEscala).size)
    }

    @Test
    fun unMontoNoNumericoSeTrataComoAusente() {
        // La validación impide aplicarlo, pero el predicado conserva el
        // comportamiento original: valor no numérico = sin filtro de precio.
        assertEquals(listOf(1L, 2L, 3L), FiltrosPedidos(montoMin = "abc").filtrar(pedidos).map { it.idPedido })
    }

    // ------------------------------------------------------- combinaciones
    @Test
    fun losCamposEnBlancoNoFiltranNada() {
        val enBlanco = FiltrosPedidos(estado = " ", desde = "", hasta = "  ", montoMin = "", montoMax = " ")
        assertEquals(listOf(1L, 2L, 3L), enBlanco.filtrar(pedidos).map { it.idPedido })
        assertNull(enBlanco.errorDeValidacion())
    }

    @Test
    fun losFiltrosSimultaneosSeCombinanConY() {
        val filtros = FiltrosPedidos(
            estado = "CANCELADO",
            desde = "2024-07-01",
            hasta = "2024-07-31",
            montoMin = "150",
            montoMax = "250"
        )
        assertEquals(listOf(3L), filtros.filtrar(pedidos).map { it.idPedido })

        // Cambiar UN criterio que no cumple deja la lista vacía (es un Y).
        val queFalla = filtros.copy(estado = "ENTREGADO")
        assertTrue(queFalla.filtrar(pedidos).isEmpty())
    }

    @Test
    fun sinCoincidenciasDevuelveListaVacia() {
        assertTrue(FiltrosPedidos(estado = "REGISTRADO", desde = "2030-01-01").filtrar(pedidos).isEmpty())
    }

    @Test
    fun elOrdenDeLaListaOriginalSeConserva() {
        val filtrados = FiltrosPedidos(montoMin = "1").filtrar(pedidos)
        assertEquals(listOf(1L, 2L, 3L), filtrados.map { it.idPedido })
    }

    // ------------------------------------------------------------ validación
    @Test
    fun sinFiltrosLaValidacionEsNull() {
        assertNull(FiltrosPedidos().errorDeValidacion())
    }

    @Test
    fun unaFechaSinFormatoProduceElMensajeExacto() {
        assertEquals(
            "Usa fechas reales con formato AAAA-MM-DD.",
            FiltrosPedidos(desde = "no-es-fecha").errorDeValidacion()
        )
        assertEquals(
            "Usa fechas reales con formato AAAA-MM-DD.",
            FiltrosPedidos(hasta = "2024/07/20").errorDeValidacion()
        )
    }

    @Test
    fun unaFechaInicialPosteriorProduceElMensajeExacto() {
        assertEquals(
            "La fecha inicial no puede superar la fecha final.",
            FiltrosPedidos(desde = "2024-05-02", hasta = "2024-05-01").errorDeValidacion()
        )
    }

    @Test
    fun unMontoNoNumericoProduceElMensajeExacto() {
        assertEquals(
            "Ingresa montos válidos.",
            FiltrosPedidos(montoMin = "doce").errorDeValidacion()
        )
        assertEquals(
            "Ingresa montos válidos.",
            FiltrosPedidos(montoMax = "12,5").errorDeValidacion()
        )
        assertEquals(
            "Ingresa montos válidos.",
            FiltrosPedidos(montoMin = "12..5").errorDeValidacion()
        )
    }

    @Test
    fun unMontoConPuntoFinalSigueSiendoValido() {
        // `BigDecimal("12.")` parsea en Java: la pantalla original tampoco
        // lo rechazaba, así que no hay error de validación.
        assertNull(FiltrosPedidos(montoMax = "12.").errorDeValidacion())
    }

    @Test
    fun unMontoNegativoProduceElMensajeExacto() {
        assertEquals(
            "Los montos no pueden ser negativos.",
            FiltrosPedidos(montoMin = "-1").errorDeValidacion()
        )
        assertEquals(
            "Los montos no pueden ser negativos.",
            FiltrosPedidos(montoMax = "-0.01").errorDeValidacion()
        )
    }

    @Test
    fun unMinimoMayorQueElMaximoProduceElMensajeExacto() {
        assertEquals(
            "El monto mínimo no puede superar al máximo.",
            FiltrosPedidos(montoMin = "200", montoMax = "100").errorDeValidacion()
        )
    }

    @Test
    fun lasFechasLimiteIgualesSonValidas() {
        assertNull(FiltrosPedidos(desde = "2024-05-01", hasta = "2024-05-01").errorDeValidacion())
        assertNull(FiltrosPedidos(montoMin = "120", montoMax = "120").errorDeValidacion())
    }

    @Test
    fun elOrdenDeEvaluacionPoneLasFechasAntesQueLosMontos() {
        // Tiene fecha inválida Y monto no numérico: gana el mensaje de fechas
        // (es el primer caso del `when` original).
        assertEquals(
            "Usa fechas reales con formato AAAA-MM-DD.",
            FiltrosPedidos(desde = "mala", montoMin = "x").errorDeValidacion()
        )
        // Fecha inicial posterior Y monto no numérico: gana el orden de fechas.
        assertEquals(
            "La fecha inicial no puede superar la fecha final.",
            FiltrosPedidos(desde = "2024-05-02", hasta = "2024-05-01", montoMin = "x").errorDeValidacion()
        )
    }

    @Test
    fun elOrdenDeEvaluacionPoneLosNegativosAntesQueElRango() {
        // Mínimo negativo Y mínimo mayor que máximo: gana el de negativos.
        assertEquals(
            "Los montos no pueden ser negativos.",
            FiltrosPedidos(montoMin = "-1", montoMax = "-5").errorDeValidacion()
        )
    }
}
