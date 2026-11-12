package com.ejemplo.publicarproducto.model.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.mapper.Mapeadores
import com.ejemplo.publicarproducto.model.NotificacionResponse
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.ejemplo.publicarproducto.testing.esperarCondicion

/**
 * Grupo D-3 - observarAvisos de NotificacionesRepositorio (HU 46).
 *
 * Verifica con Room en memoria que el flujo expuesto por el repositorio
 * devuelve los avisos del usuario mapeados con `Mapeadores.aDto`, en el
 * mismo orden de la consulta del DAO, aislado por usuario y actualizado en
 * cada cambio de la copia local. No usa backend real.
 */
@RunWith(AndroidJUnit4::class)
class NotificacionesRepositorioAvisosTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var servidor: ServidorDePruebas
    private lateinit var cola: ColaOperacionesRepositorio
    private lateinit var repositorio: NotificacionesRepositorio

    private val usuarioId = 1L

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        servidor = ServidorDePruebas()
        cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        repositorio = NotificacionesRepositorio(bd, servidor.api, cola)
    }

    @After
    fun tearDown() {
        bd.close()
        servidor.cerrar()
    }

    private fun aviso(
        id: Long,
        fecha: String,
        usuario: Long = usuarioId,
        tipo: String = "CAMBIO_ESTADO_PEDIDO",
        idPedido: Long? = 55L
    ) = Mapeadores.aEntidad(
        dto = NotificacionResponse(
            idNotificacion = id,
            tipo = tipo,
            resumen = "El pedido $idPedido cambió de estado",
            fecha = fecha,
            estado = "PENDIENTE",
            idPedido = idPedido,
            enlace = "/pedidos/$idPedido",
            idConversacion = null,
            fechaLectura = null,
            productoId = null
        ),
        usuarioId = usuario,
        ahora = 1_000L
    )

    @Test
    fun observarAvisosDevuelveLaCopiaLocalMapeada() = runBlocking {
        bd.notificacionDao().guardarTodas(listOf(aviso(id = 10, fecha = "2024-05-01T10:15:30")))

        val avisos = repositorio.observarAvisos(usuarioId).first()

        assertEquals(1, avisos.size)
        val aviso = avisos.first()
        assertEquals(10L, aviso.idNotificacion)
        assertEquals("CAMBIO_ESTADO_PEDIDO", aviso.tipo)
        // `Mapeadores.aEntidad` normaliza la fecha al formato fijo local
        // (`yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS`), igual que en producción.
        assertEquals("2024-05-01T10:15:30.000000000", aviso.fecha)
        assertEquals("PENDIENTE", aviso.estado)
        assertEquals(55L, aviso.idPedido)
        assertEquals("/pedidos/55", aviso.enlace)
    }

    @Test
    fun conservaElOrdenDeLaConsultaDelDao() = runBlocking {
        // NotificacionDao ordena por `fecha DESC, idNotificacion DESC`.
        bd.notificacionDao().guardarTodas(
            listOf(
                aviso(id = 10, fecha = "2024-05-01T10:00:00"),
                aviso(id = 30, fecha = "2024-05-03T10:00:00"),
                aviso(id = 20, fecha = "2024-05-02T10:00:00")
            )
        )

        val avisos = repositorio.observarAvisos(usuarioId).first()

        assertEquals(listOf(30L, 20L, 10L), avisos.map { it.idNotificacion })
    }

    @Test
    fun losAvisosDeOtroUsuarioNoAparecen() = runBlocking {
        bd.notificacionDao().guardarTodas(
            listOf(
                aviso(id = 10, fecha = "2024-05-01T10:00:00", usuario = 1L),
                aviso(id = 20, fecha = "2024-05-02T10:00:00", usuario = 2L)
            )
        )

        val avisos = repositorio.observarAvisos(usuarioId).first()

        assertEquals(listOf(10L), avisos.map { it.idNotificacion })
    }

    @Test
    fun observarAvisosSeActualizaEnCadaCambioDeRoom() = runBlocking {
        val emitidos = mutableListOf<List<NotificacionResponse>>()
        val trabajo = launch {
            repositorio.observarAvisos(usuarioId).take(2).collect { emitidos.add(it) }
        }
        esperar { emitidos.size == 1 }
        assertTrue(emitidos[0].isEmpty())

        bd.notificacionDao().guardarTodas(listOf(aviso(id = 10, fecha = "2024-05-01T10:00:00")))
        esperar { emitidos.size == 2 }

        assertEquals(listOf(10L), emitidos[1].map { it.idNotificacion })
        trabajo.cancel()
    }

    @Test
    fun losAvisosSeConsultanSinConexion() = runBlocking {
        bd.notificacionDao().guardarTodas(listOf(aviso(id = 10, fecha = "2024-05-01T10:00:00")))

        servidor.apagar()

        val avisos = repositorio.observarAvisos(usuarioId).first()
        assertEquals(1, avisos.size)
        assertEquals("El pedido 55 cambió de estado", avisos.first().resumen)
    }

    private suspend fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) {
        esperarCondicion(descripcion) { condicion() }
    }
}
