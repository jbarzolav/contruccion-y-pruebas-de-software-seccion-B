package com.ejemplo.publicarproducto.view.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1/R3 - Regla de arbitraje del sondeo de HU39.
 *
 * El sondeo de `GET /api/conversaciones` debe tener UN solo responsable y solo
 * debe correr con la aplicación EN PRIMER PLANO:
 *
 * - con la pestaña Consultas activa → sondea el ViewModel de la pantalla;
 * - en el resto de pestañas y en primer plano → sondea el ViewModel de avisos;
 * - en segundo plano → no sondea nadie.
 *
 * La regla es una función pura para poder verificarla en la JVM, sin
 * levantar la interfaz ni el emulador.
 */
class ArbitrajeSondeoTest {

    @Test
    fun `con la pestana Consultas activa sondea el ViewModel de la pantalla`() {
        assertFalse(
            "Con Consultas activa, el ViewModel de avisos NO debe sondear",
            debeSonearAvisosDeConversacion(
                consultasActiva = true,
                appEnPrimerPlano = true
            )
        )
    }

    @Test
    fun `en otra pestana y en primer plano sondea el ViewModel de avisos`() {
        assertTrue(
            "En otra pestaña, en primer plano, debe sondear el ViewModel de avisos",
            debeSonearAvisosDeConversacion(
                consultasActiva = false,
                appEnPrimerPlano = true
            )
        )
    }

    @Test
    fun `en segundo plano no sondea nadie`() {
        assertFalse(
            "R1 - En segundo plano no debe haber peticiones HTTP",
            debeSonearAvisosDeConversacion(
                consultasActiva = false,
                appEnPrimerPlano = false
            )
        )
        assertFalse(
            "R1 - En segundo plano no debe haber peticiones HTTP",
            debeSonearAvisosDeConversacion(
                consultasActiva = true,
                appEnPrimerPlano = false
            )
        )
    }

    @Test
    fun `nunca hay dos responsables a la vez`() {
        val combinaciones = listOf(
            false to false,
            false to true,
            true to false,
            true to true
        )

        combinaciones.forEach { (consultasActiva, primerPlano) ->
            val avisos = debeSonearAvisosDeConversacion(consultasActiva, primerPlano)
            val pantalla = consultasActiva && primerPlano
            assertFalse(
                "No puede haber dos sondeos a la vez (Consultas=$consultasActiva, " +
                    "primerPlano=$primerPlano)",
                avisos && pantalla
            )
        }
    }

    // ---------------------------------------------------------------------
    // R1/M1 - La pantalla de Consultas solo es responsable si está en pantalla
    // ---------------------------------------------------------------------

    @Test
    fun `la pantalla de Consultas solo es responsable si esta realmente en pantalla`() {
        val estado = EstadoNavegacion()
        estado.irA(IndicesNavegacion.CONSULTAS)

        assertTrue(
            "En la pestaña Consultas, sin subpantalla abierta, la pantalla es la responsable",
            consultasSonResponsable(estado)
        )

        // Se abre el detalle de un producto sobre la pestaña Consultas: la
        // pantalla sale de la composición y su sondeo se detiene, así que el
        // relevo lo tiene que asumir el ViewModel de avisos.
        estado.abrirProducto(id = 7L)

        assertFalse(
            "Con una subpantalla abierta la pantalla de Consultas NO debe ser la " +
                "responsable: si lo fuera, no sondearía nadie",
            consultasSonResponsable(estado)
        )

        estado.cerrarProducto()

        assertTrue(
            "Al cerrar la subpantalla, Consultas vuelve a ser la responsable",
            consultasSonResponsable(estado)
        )
    }

    @Test
    fun `cualquier subpantalla libera el sondeo de Consultas`() {
        val subpantallas = listOf<(EstadoNavegacion) -> Unit>(
            { it.abrirProducto(1L) },
            { it.abrirEdicion(1L) },
            { it.abrirPublicacion() },
            { it.abrirConfirmacionCompra() },
            { it.abrirPedido(1L) }
        )

        subpantallas.forEach { abrir ->
            val estado = EstadoNavegacion()
            estado.irA(IndicesNavegacion.CONSULTAS)
            abrir(estado)

            assertFalse(
                "Con una subpantalla abierta la pantalla de Consultas no debe ser " +
                    "la responsable del sondeo",
                consultasSonResponsable(estado)
            )
        }
    }

    @Test
    fun `en otra pestana la pantalla de Consultas no es la responsable`() {
        val estado = EstadoNavegacion()
        estado.irA(IndicesNavegacion.INICIO)

        assertFalse(
            "En otra pestaña la pantalla de Consultas no es la responsable",
            consultasSonResponsable(estado)
        )
    }

    // ---------------------------------------------------------------------
    // R1/M2 - Qué hacer con el sondeo al salir de la pantalla autenticada
    // ---------------------------------------------------------------------

    @Test
    fun `al salir con la sesion abierta solo se pausa y al cerrarla se limpia todo`() {
        assertEquals(
            "Con la sesión abierta (rotación) solo se pausa el sondeo: se conserva " +
                "la memoria de los mensajes vistos",
            AccionAlSalir.PAUSAR,
            accionAlSalirDeLaComposicion(usuarioActivo = true)
        )
        assertEquals(
            "Con la sesión cerrada se limpia todo, incluida la memoria de mensajes",
            AccionAlSalir.DETENER,
            accionAlSalirDeLaComposicion(usuarioActivo = false)
        )
    }
}
