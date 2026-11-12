package com.ejemplo.publicarproducto.domain

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Grupo C - Reglas del carrito (HU 12).
 *
 * Pruebas unitarias de lógica pura: no necesitan emulador ni servidor.
 */
class ReglasCarritoTest {

    @Test
    fun sumarUnoDevuelveLaCantidadSiguiente() {
        assertEquals(3, ReglasCarrito.nuevaCantidad(2, 1))
    }

    @Test
    fun restarUnoDevuelveLaCantidadAnterior() {
        assertEquals(1, ReglasCarrito.nuevaCantidad(2, -1))
    }

    @Test
    fun laCantidadMinimaEsUno() {
        assertEquals(1, ReglasCarrito.CANTIDAD_MINIMA)
    }

    @Test
    fun noSePuedeBajarDeUno() {
        assertNull(ReglasCarrito.nuevaCantidad(1, -1))
    }

    @Test
    fun noSePuedeBajarDeCero() {
        assertNull(ReglasCarrito.nuevaCantidad(0, -1))
    }

    @Test
    fun unDeltaNegativoGrandeSeRechaza() {
        assertNull(ReglasCarrito.nuevaCantidad(3, -10))
    }

    @Test
    fun unDeltaPositivoSiempreEsValido() {
        assertEquals(15, ReglasCarrito.nuevaCantidad(3, 12))
    }
}
