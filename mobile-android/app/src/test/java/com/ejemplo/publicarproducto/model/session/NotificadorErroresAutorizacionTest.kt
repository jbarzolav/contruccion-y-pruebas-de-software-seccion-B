package com.ejemplo.publicarproducto.model.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Fase E - El cliente HTTP (model/remote) notifica los errores de
 * autorización en Model y la presentación los consume desde su ViewModel.
 *
 * - 401: la sesión caducó y se publica en el bus de sesión (con memoria).
 * - 403: el usuario no tiene permiso y se entrega sin memoria, para no
 *   mostrar avisos obsoletos al recrear la pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificadorErroresAutorizacionTest {

    @Test
    fun `el 401 publica la sesion expirada en el bus`() = runTest {
        BusEventosSesion.limpiarReplayParaPruebas()
        val evento = async(UnconfinedTestDispatcher(testScheduler)) {
            BusEventosSesion.eventos.first()
        }
        NotificadorErroresAutorizacion.notificar(401)
        assertEquals(EventoSesion.SesionExpirada, evento.await())
    }

    @Test
    fun `el 403 se entrega al observador`() = runTest {
        val codigo = async(UnconfinedTestDispatcher(testScheduler)) {
            NotificadorErroresAutorizacion.errores.first()
        }
        NotificadorErroresAutorizacion.notificar(403)
        assertEquals(403, codigo.await())
    }

    @Test
    fun `el 401 no se entrega por el flujo de permisos`() = runTest {
        BusEventosSesion.limpiarReplayParaPruebas()
        val permisos = async(UnconfinedTestDispatcher(testScheduler)) {
            NotificadorErroresAutorizacion.errores.first()
        }
        val sesion = async(UnconfinedTestDispatcher(testScheduler)) {
            BusEventosSesion.eventos.first()
        }
        NotificadorErroresAutorizacion.notificar(401)
        assertEquals(EventoSesion.SesionExpirada, sesion.await())
        // El flujo de permisos sigue esperando: no recibió el 401.
        assertFalse(permisos.isCompleted)
        permisos.cancel()
    }
}
