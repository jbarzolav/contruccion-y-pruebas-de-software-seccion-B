package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.model.conversaciones.ConversacionResumenResponse
import com.ejemplo.publicarproducto.model.conversaciones.MensajeConsulta
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvisosConversacionViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sondeoOmiteCargaInicialToleraErroresYNotificaUnaSolaVezPorMensaje() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var llamada = 0
        val respuestas = listOf(
            ResultadoApi.Exito(listOf(resumen(10, "Mensaje anterior", 7))),
            null, // Simula un fallo temporal en una iteración.
            ResultadoApi.Exito(listOf(resumen(11, "Producto nuevo", 7))),
            ResultadoApi.Exito(listOf(resumen(11, "No repetir", 7)))
        )
        val viewModel = AvisosConversacionViewModel {
            llamada++
            respuestas.getOrNull(llamada - 1)
                ?: throw java.io.IOException("sin conexión temporal")
        }

        try {
            viewModel.iniciar(usuarioId = 42)
            runCurrent()
            assertNull("La carga inicial no debe avisar por mensajes anteriores", viewModel.avisoMensaje)

            advanceTimeBy(15_000)
            runCurrent()
            assertNull("Un fallo temporal no debe bloquear ni emitir un aviso", viewModel.avisoMensaje)

            advanceTimeBy(15_000)
            runCurrent()
            assertEquals("Producto nuevo", viewModel.avisoMensaje)

            advanceTimeBy(15_000)
            runCurrent()
            assertEquals("El mismo mensaje no debe volver a notificarse", "Producto nuevo", viewModel.avisoMensaje)
            assertEquals(4, llamada)
        } finally {
            viewModel.detener()
            Dispatchers.resetMain()
        }
    }

    private fun resumen(idMensaje: Long, producto: String, emisorId: Long) =
        ConversacionResumenResponse(
            idConversacion = 1,
            productoId = 2,
            productoNombre = producto,
            productoImagenUrl = null,
            fechaCreacion = "2025-01-01T00:00:00",
            ultimoMensaje = MensajeConsulta(
                id = idMensaje,
                emisorId = emisorId,
                emisorRol = "CLIENTE",
                contenido = "Mensaje",
                fecha = "2025-01-01T00:00:00"
            )
        )
}
