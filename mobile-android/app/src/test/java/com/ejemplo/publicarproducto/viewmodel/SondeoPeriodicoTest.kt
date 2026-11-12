package com.ejemplo.publicarproducto.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1/R3 - Ciclo del sondeo periódico de HU39.
 *
 * Verifica el contrato del único trabajo de sondeo de la aplicación:
 * - espera el intervalo completo antes de la primera ejecución (el
 *   comportamiento que tenía el `LaunchedEffect` de la pantalla);
 * - ejecuta UNA vez por intervalo;
 * - `iniciar` dos veces NO duplica el trabajo (R3: una sola petición por
 *   intervalo, sin doble fuente de sondeo);
 * - `detener` cancela el trabajo de inmediato (R1: sin peticiones en segundo
 *   plano ni al abandonar la pantalla);
 * - un fallo puntual de una iteración no detiene el sondeo;
 * - la cancelación se propaga como `CancellationException` (no se convierte
 *   en un error visible).
 *
 * El tiempo es virtual (`kotlinx-coroutines-test`): la prueba no espera
 * 15 segundos reales y no depende de la red.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SondeoPeriodicoTest {

    @Test
    fun laPrimeraEjecucionEsperaElIntervaloCompleto() = runTest {
        val ejecuciones = mutableListOf<String>()
        val sondeo = SondeoPeriodico(
            alcance = backgroundScope,
            intervaloMs = { 15_000L },
            bloque = { ejecuciones += "refresco" }
        )

        sondeo.iniciar()
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals("No debe refrescar antes de cumplir el intervalo", 0, ejecuciones.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, ejecuciones.size)
    }

    @Test
    fun ejecutaUnaSolaVezPorIntervalo() = runTest {
        val ejecuciones = mutableListOf<String>()
        val sondeo = SondeoPeriodico(
            alcance = backgroundScope,
            intervaloMs = { 15_000L },
            bloque = { ejecuciones += "refresco" }
        )

        sondeo.iniciar()
        advanceTimeBy(45_000)
        runCurrent()

        assertEquals("Debe haber un refresco por cada intervalo cumplido", 3, ejecuciones.size)
    }

    @Test
    fun iniciarDosVecesNoDuplicaElSondeo() = runTest {
        val ejecuciones = mutableListOf<String>()
        val sondeo = SondeoPeriodico(
            alcance = backgroundScope,
            intervaloMs = { 15_000L },
            bloque = { ejecuciones += "refresco" }
        )

        sondeo.iniciar()
        sondeo.iniciar()
        sondeo.iniciar()
        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(
            "Solo puede haber UN responsable del sondeo: tres arranques no pueden " +
                "generar tres peticiones por intervalo",
            1,
            ejecuciones.size
        )
    }

    @Test
    fun detenerCancelaElTrabajoEnCurso() = runTest {
        val ejecuciones = mutableListOf<String>()
        val sondeo = SondeoPeriodico(
            alcance = backgroundScope,
            intervaloMs = { 15_000L },
            bloque = { ejecuciones += "refresco" }
        )

        sondeo.iniciar()
        assertTrue(sondeo.activo)

        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(1, ejecuciones.size)

        sondeo.detener()
        assertFalse(sondeo.activo)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(
            "Tras detener el sondeo no debe haber más peticiones HTTP",
            1,
            ejecuciones.size
        )
    }

    @Test
    fun unFalloPuntualNoDetieneElSondeo() = runTest {
        val ejecuciones = mutableListOf<String>()
        val sondeo = SondeoPeriodico(
            alcance = backgroundScope,
            intervaloMs = { 15_000L },
            bloque = {
                ejecuciones += "refresco"
                throw IllegalStateException("fallo puntual del servidor")
            }
        )

        sondeo.iniciar()
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(
            "Un fallo de una iteración no debe detener el sondeo",
            2,
            ejecuciones.size
        )
        assertTrue(sondeo.activo)
    }

    @Test
    fun laCancelacionSePropagaYNoSeConvierteEnError() = runTest {
        val ejecuciones = mutableListOf<String>()
        val sondeo = SondeoPeriodico(
            alcance = backgroundScope,
            intervaloMs = { 15_000L },
            bloque = {
                ejecuciones += "refresco"
                throw CancellationException("cancelado")
            }
        )

        sondeo.iniciar()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(
            "La cancelación se propaga: el sondeo se detiene en lugar de continuar",
            1,
            ejecuciones.size
        )
        assertFalse("Tras la cancelación el sondeo no sigue activo", sondeo.activo)
    }
}
