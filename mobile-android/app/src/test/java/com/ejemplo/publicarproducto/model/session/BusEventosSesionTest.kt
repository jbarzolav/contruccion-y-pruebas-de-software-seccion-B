package com.ejemplo.publicarproducto.model.session

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Pruebas JVM del bus global de sesión, incluido el último evento en replay. */
class BusEventosSesionTest {

    @Before
    fun limpiarAntes() {
        BusEventosSesion.limpiarReplayParaPruebas()
    }

    @After
    fun limpiarDespues() {
        BusEventosSesion.limpiarReplayParaPruebas()
    }

    @Test
    fun elBusEntregaElEventoPublicado() = runBlocking {
        val observado = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1_000) { BusEventosSesion.eventos.first() }
        }

        BusEventosSesion.publicar(EventoSesion.SesionCerrada)

        assertEquals(EventoSesion.SesionCerrada, observado.await())
    }

    @Test
    fun elBusConservaElUltimoEventoParaLosNuevosObservadores() = runBlocking {
        val evento = EventoSesion.SesionIniciada(7L)
        BusEventosSesion.publicar(evento)

        val recibido = withTimeout(1_000) { BusEventosSesion.eventos.first() }

        assertEquals(evento, recibido)
    }

    @Test
    fun elBusEntregaLosEventosEnOrden() = runBlocking {
        val esperado = listOf(
            EventoSesion.SesionIniciada(1L),
            EventoSesion.SesionCerrada,
            EventoSesion.SesionExpirada
        )
        val observados = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1_000) { BusEventosSesion.eventos.take(esperado.size).toList() }
        }

        esperado.forEach(BusEventosSesion::publicar)

        assertEquals(esperado, observados.await())
    }
}
