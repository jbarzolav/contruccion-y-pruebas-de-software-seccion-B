package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import com.ejemplo.publicarproducto.model.NotificacionResponse
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import com.ejemplo.publicarproducto.testing.esperarCondicion

/**
 * Grupo D-4 - observación de los avisos del vendedor (HU 48).
 *
 * Desde D-4 el ViewModel ya no consulta `NotificacionDao`: recibe los avisos
 * del repositorio (`NotificacionesRepositorio.observarAvisos`), que entrega
 * la misma consulta, el mismo orden y el mismo mapeo.
 *
 * Aquí se verifica el comportamiento observable de esa observación -la misma
 * que usa el ViewModel- sustituyendo el repositorio por un flujo falso
 * (`MutableStateFlow`), sin Room y sin red. Se comprueba que el orden se
 * conserva, que llegan las reemisiones cuando la copia local cambia, que la
 * cancelación se propaga al flujo y que los errores no se silencian.
 */
class PedidosRecibidosViewModelAvisosTest {

    /** Aviso de prueba con los mismos campos que produce `Mapeadores.aDto`. */
    private fun aviso(
        id: Long,
        fecha: String = "2024-05-01T10:15:30",
        tipo: String? = "NUEVA_COMPRA"
    ) = NotificacionResponse(
        idNotificacion = id,
        tipo = tipo,
        resumen = "Nueva compra del pedido 55",
        fecha = fecha,
        estado = "PENDIENTE",
        idPedido = 55L,
        enlace = "/pedidos/55",
        idConversacion = null,
        fechaLectura = null,
        productoId = null
    )

    @Test
    fun publicaLaPrimeraEmisionDeAvisos() = runBlocking {
        val fuente = MutableStateFlow(listOf(aviso(10L)))
        var recibidos: List<NotificacionResponse>? = null

        val trabajo = launch {
            recopilarAvisos(fuente) { avisos -> recibidos = avisos }
        }
        esperar { recibidos != null }

        assertEquals(1, recibidos!!.size)
        assertEquals(10L, recibidos!!.first().idNotificacion)
        trabajo.cancelar()
    }

    @Test
    fun conservaElOrdenDeLosAvisos() = runBlocking {
        // El DAO ordena por `fecha DESC, idNotificacion DESC`: la observación
        // no reordena ni agrupa, respeta el orden que entrega el repositorio.
        val fuente = MutableStateFlow(
            listOf(
                aviso(30L, "2024-05-03T10:00:00"),
                aviso(20L, "2024-05-02T10:00:00"),
                aviso(10L, "2024-05-01T10:00:00")
            )
        )
        var recibidos: List<NotificacionResponse> = emptyList()

        val trabajo = launch {
            recopilarAvisos(fuente) { avisos -> recibidos = avisos }
        }
        esperar { recibidos.isNotEmpty() }

        assertEquals(listOf(30L, 20L, 10L), recibidos.map { it.idNotificacion })
        trabajo.cancelar()
    }

    @Test
    fun recibeNuevasEmisionesCuandoLaCopiaLocalCambia() = runBlocking {
        val fuente = MutableStateFlow(listOf(aviso(10L)))
        val recibidas = mutableListOf<List<NotificacionResponse>>()

        val trabajo = launch {
            recopilarAvisos(fuente) { avisos -> recibidas.add(avisos) }
        }
        esperar { recibidas.size == 1 }

        fuente.value = listOf(
            aviso(20L, "2024-05-02T10:00:00"),
            aviso(10L, "2024-05-01T10:00:00")
        )
        esperar { recibidas.size == 2 }

        assertEquals(listOf(10L), recibidas[0].map { it.idNotificacion })
        assertEquals(listOf(20L, 10L), recibidas[1].map { it.idNotificacion })
        trabajo.cancelar()
    }

    @Test
    fun unaCopiaLocalVaciaPublicaUnaListaSinAvisos() = runBlocking {
        val fuente = MutableStateFlow(emptyList<NotificacionResponse>())
        var recibidos: List<NotificacionResponse>? = null

        val trabajo = launch {
            recopilarAvisos(fuente) { avisos -> recibidos = avisos }
        }
        esperar { recibidos != null }

        assertTrue(recibidos!!.isEmpty())
        trabajo.cancelar()
    }

    @Test
    fun laCancelacionSePropagaAlFlujoDeAvisos() = runBlocking {
        var cerrado = false
        val fuente = flow {
            try {
                emit(listOf(aviso(10L)))
                awaitCancellation()
            } finally {
                cerrado = true
            }
        }
        var recibidas = 0

        val trabajo = launch {
            recopilarAvisos(fuente) { recibidas++ }
        }
        esperar { recibidas == 1 }

        trabajo.cancel()
        trabajo.join()

        assertTrue("El flujo de avisos debe recibir la cancelación", cerrado)
        assertEquals(1, recibidas)
    }

    @Test
    fun unErrorEnElFlujoDeAvisosSePropaga() = runBlocking {
        // El ViewModel no captura los fallos de la observación de avisos:
        // se conserva ese comportamiento (un fallo de la copia local no se
        // esconde detrás de un estado vacío).
        val fuente = flow<List<NotificacionResponse>> {
            throw IllegalStateException("fallo de la copia local")
        }

        try {
            recopilarAvisos(fuente) { }
            fail("El error del flujo de avisos debe propagarse")
        } catch (e: IllegalStateException) {
            assertEquals("fallo de la copia local", e.message)
        }
    }

    /** Espera en tiempo real a que se cumpla la condición. */
    private suspend fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) {
        esperarCondicion(descripcion) { condicion() }
    }

    /** Cancela el trabajo y espera a que termine. */
    private suspend fun kotlinx.coroutines.Job.cancelar() {
        cancel()
        join()
    }
}
