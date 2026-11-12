package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.model.conversaciones.ConversacionResumenResponse
import com.ejemplo.publicarproducto.model.conversaciones.MensajeConsulta
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1/R3 - Cesión del sondeo de HU39 entre los dos responsables.
 *
 * El sondeo de `GET /api/conversaciones` solo puede tener un responsable:
 *
 * - con la pestaña Consultas activa, sondea el ViewModel de la pantalla
 *   (que además refresca la conversación abierta);
 * - en el resto de pestañas, sondea el ViewModel de avisos de la barra
 *   superior.
 *
 * Estas pruebas verifican el contrato de la cesión:
 * - `pausar()` cancela el trabajo en curso: no hay peticiones HTTP mientras
 *   la pantalla de Consultas sondea (R1);
 * - `pausar()` NO borra la memoria de los mensajes ya vistos: al reanudar se
 *   detectan los mensajes que llegaron mientras el sondeo estaba cedido
 *   (R3, sin perder avisos);
 * - `iniciar()` dos veces NO duplica el trabajo: una sola petición por
 *   intervalo;
 * - el cambio de usuario sí reinicia la memoria (los avisos de una cuenta
 *   no se muestran en otra).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AvisosConversacionSondeoTest {

    @Test
    fun iniciarDosVecesNoDuplicaElSondeo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var llamadas = 0
        val viewModel = AvisosConversacionViewModel {
            llamadas++
            ResultadoApi.Exito(emptyList())
        }

        try {
            viewModel.iniciar(usuarioId = 42)
            viewModel.iniciar(usuarioId = 42)
            runCurrent()

            assertEquals(
                "Solo puede haber UN trabajo de sondeo activo",
                1,
                llamadas
            )

            advanceTimeBy(15_000)
            runCurrent()
            assertEquals(
                "Un solo responsable: una petición por intervalo",
                2,
                llamadas
            )
        } finally {
            viewModel.detener()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pausarCancelaElSondeoYNoHayPeticionesEnSegundoPlano() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var llamadas = 0
        val viewModel = AvisosConversacionViewModel {
            llamadas++
            ResultadoApi.Exito(emptyList())
        }

        try {
            viewModel.iniciar(usuarioId = 42)
            runCurrent()
            assertEquals(1, llamadas)

            viewModel.pausar()
            assertTrue("Tras pausar, el sondeo queda cedido", viewModel.cedido())

            advanceTimeBy(60_000)
            runCurrent()

            assertEquals(
                "Con el sondeo cedido no debe haber ninguna petición HTTP",
                1,
                llamadas
            )
        } finally {
            viewModel.detener()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reanudarTrasPausarDetectaLosMensajesQueLlegaronDuranteLaCesion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // El "servidor" devuelve el mensaje 10 al principio y el 11 después.
        var mensajeActual = 10L
        var llamadas = 0
        val viewModel = AvisosConversacionViewModel {
            llamadas++
            ResultadoApi.Exito(listOf(resumen(mensajeActual, "Producto nuevo")))
        }

        try {
            viewModel.iniciar(usuarioId = 42)
            runCurrent()
            assertNull(
                "La carga inicial no avisa por los mensajes anteriores",
                viewModel.avisoMensaje
            )

            // La pantalla de Consultas entra en pantalla: el sondeo se cede.
            viewModel.pausar()
            assertTrue(viewModel.cedido())

            // Mientras el sondeo está cedido, llega un mensaje nuevo.
            mensajeActual = 11L

            // La pantalla de Consultas sale: el sondeo vuelve a este ViewModel.
            viewModel.iniciar(usuarioId = 42)
            runCurrent()

            assertFalse("Tras reanudar, el sondeo vuelve a ser de este ViewModel", viewModel.cedido())
            assertEquals(
                "No se deben perder los avisos que llegaron durante la cesión",
                "Producto nuevo",
                viewModel.avisoMensaje
            )
            assertEquals(2, llamadas)
        } finally {
            viewModel.detener()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cambiarDeUsuarioReiniciaLaMemoriaDeMensajesVistos() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var mensajeActual = 10L
        var llamadas = 0
        val viewModel = AvisosConversacionViewModel {
            llamadas++
            ResultadoApi.Exito(listOf(resumen(mensajeActual, "Producto nuevo")))
        }

        try {
            viewModel.iniciar(usuarioId = 42)
            runCurrent()
            assertNull(viewModel.avisoMensaje)

            // Cambia el usuario: la memoria de mensajes vistos se reinicia,
            // así que la carga inicial del usuario nuevo no avisa.
            mensajeActual = 11L
            viewModel.iniciar(usuarioId = 99)
            runCurrent()

            assertNull(
                "El mensaje ajeno no debe avisar en la sesión del usuario nuevo",
                viewModel.avisoMensaje
            )
        } finally {
            viewModel.detener()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unMensajePropioNoGeneraAviso() = runTest {        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var mensajeActual = 10L
        val viewModel = AvisosConversacionViewModel {
            ResultadoApi.Exito(listOf(resumen(mensajeActual, "Producto nuevo", emisorId = 42L)))
        }

        try {
            viewModel.iniciar(usuarioId = 42)
            runCurrent()

            mensajeActual = 11L
            advanceTimeBy(15_000)
            runCurrent()

            assertNull(
                "Un mensaje enviado por el propio usuario no genera aviso",
                viewModel.avisoMensaje
            )
        } finally {
            viewModel.detener()
            Dispatchers.resetMain()
        }
    }

    // ---------------------------------------------------------------------
    // R1/M2 - Qué se conserva al salir de la pantalla autenticada
    // ---------------------------------------------------------------------

    @Test
    fun laRecreacionDeLaActivityConservaElAvisoYLaMemoriaDeMensajes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var mensajeActual = 10L
        var productoActual = "Producto inicial"
        val viewModel = AvisosConversacionViewModel {
            ResultadoApi.Exito(listOf(resumen(mensajeActual, productoActual)))
        }

        try {
            viewModel.iniciar(usuarioId = 42)
            runCurrent()

            mensajeActual = 11L
            productoActual = "Producto nuevo"
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals("Producto nuevo", viewModel.avisoMensaje)

            // R1/M2 - Recreación de la Activity (rotación): la sesión sigue
            // abierta, así que solo se pausa el sondeo.
            viewModel.pausar()

            assertEquals(
                "En una recreación de la Activity se conserva el aviso de la barra superior",
                "Producto nuevo",
                viewModel.avisoMensaje
            )

            // Llega otro mensaje durante la recreación: al reanudar debe
            // detectarse (la memoria de mensajes vistos se conservó).
            mensajeActual = 12L
            productoActual = "Producto posterior"
            viewModel.iniciar(usuarioId = 42)
            runCurrent()

            assertEquals(
                "No se deben perder los mensajes que llegan durante la recreación",
                "Producto posterior",
                viewModel.avisoMensaje
            )
        } finally {
            viewModel.detener()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun alSalirDeLaSesionSeLimpianElAvisoYLaMemoriaDeMensajes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var mensajeActual = 10L
        var productoActual = "Producto inicial"
        val viewModel = AvisosConversacionViewModel {
            ResultadoApi.Exito(listOf(resumen(mensajeActual, productoActual)))
        }

        try {
            viewModel.iniciar(usuarioId = 42)
            runCurrent()

            mensajeActual = 11L
            productoActual = "Producto nuevo"
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals("Producto nuevo", viewModel.avisoMensaje)

            // R1/M2 - Salida de la sesión: se limpia todo.
            viewModel.detener()

            assertNull(
                "Al salir de la sesión se borra el aviso de la barra superior",
                viewModel.avisoMensaje
            )

            // La memoria también está limpia: el mensaje ajeno ya no se
            // vuelve a marcar como visto, así que no vuelve a avisar.
            viewModel.iniciar(usuarioId = 42)
            runCurrent()

            assertNull(
                "Tras limpiar la memoria, la carga inicial no debe avisar",
                viewModel.avisoMensaje
            )
        } finally {
            viewModel.detener()
            Dispatchers.resetMain()
        }
    }

    private fun resumen(idMensaje: Long, producto: String, emisorId: Long = 7L) =
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
