package com.ejemplo.publicarproducto.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.local.EstadosOperacion
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mejora técnica Offline First - bandeja de notificaciones.
 *
 * Verifica el ciclo real: el backend responde, la copia local de Room se
 * actualiza, la lectura guardada en el dispositivo queda pendiente hasta que
 * el servidor la confirma y un rechazo del servidor revierte lo local.
 */
@RunWith(AndroidJUnit4::class)
class NotificacionesRepositorioTest {

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

    private fun jsonBandeja(estado: String = "PENDIENTE", pendientes: Long = 1L) = """
        {
          "notificaciones": [
            {
              "idNotificacion": 10,
              "tipo": "NUEVO_MENSAJE",
              "resumen": "El cliente te envió un mensaje.",
              "fecha": "2024-05-01T10:15:30",
              "estado": "$estado",
              "idPedido": null,
              "enlace": "/consultas?c=1",
              "idConversacion": 7,
              "fechaLectura": null
            }
          ],
          "pagina": 0,
          "tamano": 20,
          "totalElementos": 1,
          "totalPaginas": 1,
          "tieneSiguiente": false,
          "pendientes": $pendientes
        }
    """.trimIndent()

    @Test
    fun refrescarGuardaLaRespuestaRealDelServidorEnRoom() = runTest {
        servidor.responder(200, jsonBandeja())

        val resultado = repositorio.refrescar(usuarioId, null, null, null, null, 0)
        assertTrue(resultado is ResultadoApi.Exito)

        val aviso = bd.notificacionDao().buscar(usuarioId, 10L)
        assertNotNull(aviso)
        assertEquals("El cliente te envió un mensaje.", aviso!!.resumen)
        assertEquals("PENDIENTE", aviso.estado)

        val bandeja = repositorio.observarBandeja(usuarioId, null, null, null, null, 0).first()
        assertEquals(1, bandeja.notificaciones.size)
        assertEquals(1L, bandeja.pendientes)
    }

    @Test
    fun laConsultaSinInternetDevuelveLaCopiaLocal() = runTest {
        servidor.responder(200, jsonBandeja())
        repositorio.refrescar(usuarioId, null, null, null, null, 0)

        // Se apaga el servidor: la consulta local debe seguir funcionando.
        servidor.apagar()
        val bandeja = repositorio.observarBandeja(usuarioId, null, null, null, null, 0).first()

        assertEquals(1, bandeja.notificaciones.size)
        assertEquals("El cliente te envió un mensaje.", bandeja.notificaciones.first().resumen)
    }

    @Test
    fun laLecturaLocalQuedaPendienteHastaQueElServidorConfirma() = runTest {
        servidor.responder(200, jsonBandeja())
        repositorio.refrescar(usuarioId, null, null, null, null, 0)

        repositorio.registrarLecturaLocal(usuarioId, 10L)
        repositorio.encolarLectura(usuarioId, 10L)

        val aviso = bd.notificacionDao().buscar(usuarioId, 10L)
        assertEquals("LEIDA", aviso!!.estado)
        assertTrue(aviso.pendienteLecturaLocal)

        val pendientes = cola.observarPendientes(usuarioId).first()
        assertEquals(1, pendientes.size)
        assertEquals(EstadosOperacion.PENDIENTE, pendientes.first().estado)

        // El contador global baja porque la lectura ya no está pendiente.
        assertEquals(0L, bd.bandejaDao().buscarPendientesGlobales(usuarioId))
    }

    @Test
    fun elRechazoDelServidorRevierteLaLecturaLocal() = runTest {
        servidor.responder(200, jsonBandeja())
        repositorio.refrescar(usuarioId, null, null, null, null, 0)
        repositorio.registrarLecturaLocal(usuarioId, 10L)

        // El sincronizador revierte lo local cuando el servidor rechaza.
        repositorio.revertirLectura(usuarioId, 10L)

        val aviso = bd.notificacionDao().buscar(usuarioId, 10L)
        assertEquals("PENDIENTE", aviso!!.estado)
        assertTrue(!aviso.pendienteLecturaLocal)
        assertEquals(1L, bd.bandejaDao().buscarPendientesGlobales(usuarioId))
    }

    @Test
    fun laColaNoDuplicaLaMismaOperacion() = runTest {
        val primera = cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = com.ejemplo.publicarproducto.local.TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "10",
            payload = null,
            claveUnica = "notificacion:10"
        )
        val segunda = cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = com.ejemplo.publicarproducto.local.TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "10",
            payload = null,
            claveUnica = "notificacion:10"
        )

        assertEquals(primera.id, segunda.id)
        assertEquals(1, cola.observarPendientes(usuarioId).first().size)
    }

    @Test
    fun lasOperacionesDeUnUsuarioNoSeMezclanConLasDeOtro() = runTest {
        cola.encolar(
            usuarioId = 1L,
            tipoOperacion = com.ejemplo.publicarproducto.local.TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "10",
            payload = null,
            claveUnica = "notificacion:10"
        )
        cola.encolar(
            usuarioId = 2L,
            tipoOperacion = com.ejemplo.publicarproducto.local.TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "20",
            payload = null,
            claveUnica = "notificacion:20"
        )

        assertEquals(1, cola.observarPendientes(1L).first().size)
        assertEquals(1, cola.observarPendientes(2L).first().size)
        assertNull(bd.operacionPendienteDao().buscarActivaPorClave(2L, "notificacion:10"))
    }
}
