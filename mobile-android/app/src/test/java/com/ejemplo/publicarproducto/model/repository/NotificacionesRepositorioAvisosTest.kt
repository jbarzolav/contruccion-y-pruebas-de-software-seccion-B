package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.local.EntidadNotificacion
import com.ejemplo.publicarproducto.model.NotificacionResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ejemplo.publicarproducto.testing.esperarCondicion

/**
 * Grupo D-3 - observarAvisos de NotificacionesRepositorio (HU 46).
 *
 * Pruebas unitarias (JVM, sin emulador) del mapeo que consume la pantalla
 * "Mis pedidos": la función es EXACTAMENTE la que usa `observarAvisos` con
 * el Flow real de `NotificacionDao.observarTodas`, de modo que aquí se
 * verifica el mapeo a `NotificacionResponse`, la conservación del orden de
 * la consulta y las reemisiones cuando Room cambia.
 *
 * Room no existe en la JVM: se sustituye por un `MutableStateFlow` con las
 * mismas entidades que devuelve el DAO.
 */
class NotificacionesRepositorioAvisosTest {

    private fun entidad(
        id: Long,
        fecha: String,
        usuarioId: Long = 1L,
        tipo: String? = "CAMBIO_ESTADO_PEDIDO",
        resumen: String? = "El pedido cambió de estado",
        idPedido: Long? = 55L
    ) = EntidadNotificacion(
        usuarioId = usuarioId,
        idNotificacion = id,
        tipo = tipo,
        resumen = resumen,
        fecha = fecha,
        estado = "PENDIENTE",
        idPedido = idPedido,
        enlace = "/pedidos/55",
        idConversacion = null,
        fechaLectura = null,
        fechaSincronizacion = 1_000L,
        productoId = null,
        pendienteLecturaLocal = false
    )

    @Test
    fun elFlujoDeAvisosMapeaLasEntidadesADtos() = runBlocking {
        val fuente = MutableStateFlow(listOf(entidad(id = 10, fecha = "2024-05-01T10:15:30")))

        val avisos = avisosDesde(fuente).first()

        assertEquals(1, avisos.size)
        val aviso = avisos.first()
        assertEquals(10L, aviso.idNotificacion)
        assertEquals("CAMBIO_ESTADO_PEDIDO", aviso.tipo)
        assertEquals("El pedido cambió de estado", aviso.resumen)
        assertEquals("2024-05-01T10:15:30", aviso.fecha)
        assertEquals("PENDIENTE", aviso.estado)
        assertEquals(55L, aviso.idPedido)
        assertEquals("/pedidos/55", aviso.enlace)
    }

    @Test
    fun conservaElOrdenDeLaConsultaDelDao() = runBlocking {
        // El DAO ordena por `fecha DESC, idNotificacion DESC`: el flujo
        // respeta ese orden sin reordenar ni agrupar.
        val fuente = MutableStateFlow(
            listOf(
                entidad(id = 30, fecha = "2024-05-03T10:00:00"),
                entidad(id = 20, fecha = "2024-05-02T10:00:00"),
                entidad(id = 10, fecha = "2024-05-01T10:00:00")
            )
        )

        val avisos = avisosDesde(fuente).first()

        assertEquals(listOf(30L, 20L, 10L), avisos.map { it.idNotificacion })
    }

    @Test
    fun unaConsultaVaciaDevuelveListaVacia() = runBlocking {
        val avisos = avisosDesde(MutableStateFlow(emptyList())).first()

        assertTrue(avisos.isEmpty())
    }

    @Test
    fun elMapeoConservaLosCamposNulosDelAviso() = runBlocking {
        val fuente = MutableStateFlow(
            listOf(
                EntidadNotificacion(
                    usuarioId = 1L,
                    idNotificacion = 7,
                    tipo = null,
                    resumen = null,
                    fecha = null,
                    estado = null,
                    idPedido = null,
                    enlace = null,
                    idConversacion = null,
                    fechaLectura = null,
                    fechaSincronizacion = 0L,
                    productoId = null,
                    pendienteLecturaLocal = false
                )
            )
        )

        val aviso = avisosDesde(fuente).first().first()

        assertEquals(7L, aviso.idNotificacion)
        assertEquals(null, aviso.tipo)
        assertEquals(null, aviso.resumen)
        assertEquals(null, aviso.fecha)
        assertEquals(null, aviso.estado)
        assertEquals(null, aviso.idPedido)
        assertEquals(null, aviso.productoId)
    }

    @Test
    fun emiteCadaVezQueLosAvisosDeRoomCambian() = runBlocking {
        val fuente = MutableStateFlow(listOf(entidad(id = 10, fecha = "2024-05-01T10:00:00")))
        val emitidos = mutableListOf<List<NotificacionResponse>>()

        val trabajo = launch {
            avisosDesde(fuente).take(2).collect { emitidos.add(it) }
        }
        esperar { emitidos.size == 1 }

        fuente.value = listOf(
            entidad(id = 20, fecha = "2024-05-02T10:00:00"),
            entidad(id = 10, fecha = "2024-05-01T10:00:00")
        )
        esperar { emitidos.size == 2 }

        assertEquals(listOf(10L), emitidos[0].map { it.idNotificacion })
        assertEquals(listOf(20L, 10L), emitidos[1].map { it.idNotificacion })
        trabajo.cancel()
    }

    private suspend fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) {
        esperarCondicion(descripcion) { condicion() }
    }
}
