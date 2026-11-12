package com.ejemplo.publicarproducto.domain

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grupo B - Reglas de validación de la publicación (HU 01 y HU 03).
 *
 * Pruebas unitarias de lógica pura: no necesitan emulador ni servidor.
 * Verifican que los mensajes de error son los mismos que mostraba la
 * aplicación antes de la refactorización.
 */
class ValidacionPublicacionTest {

    private fun formularioValido(requiereImagen: Boolean = false): Map<String, String> =
        ValidadorPublicacion.validar(
            nombre = "Teclado mecánico",
            descripcion = "Teclado switches rojos",
            precio = "150.50",
            stock = "10",
            categoria = "Periféricos",
            requiereImagen = requiereImagen,
            imagenPresente = requiereImagen
        )

    @Test
    fun unFormularioCompletoNoTieneErrores() {
        assertTrue(formularioValido().isEmpty())
    }

    @Test
    fun elNombreEsObligatorio() {
        val errores = ValidadorPublicacion.validar(
            nombre = "   ",
            descripcion = "desc",
            precio = "10",
            stock = "1",
            categoria = "cat"
        )

        assertEquals("El nombre es obligatorio", errores["nombre"])
    }

    @Test
    fun laDescripcionEsObligatoria() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "",
            precio = "10",
            stock = "1",
            categoria = "cat"
        )

        assertEquals("La descripción es obligatoria", errores["descripcion"])
    }

    @Test
    fun elPrecioEsObligatorio() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "",
            stock = "1",
            categoria = "cat"
        )

        assertEquals("El precio es obligatorio", errores["precio"])
    }

    @Test
    fun elPrecioNoPuedeSerUnNumeroInvalido() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "abc",
            stock = "1",
            categoria = "cat"
        )

        assertEquals("El precio no es un número válido", errores["precio"])
    }

    @Test
    fun elPrecioDebeSerMayorACero() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "0",
            stock = "1",
            categoria = "cat"
        )

        assertEquals("El precio debe ser mayor a 0", errores["precio"])
    }

    @Test
    fun elStockEsObligatorio() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "10",
            stock = "",
            categoria = "cat"
        )

        assertEquals("El stock es obligatorio", errores["stock"])
    }

    @Test
    fun elStockNoPuedeSerUnNumeroInvalido() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "10",
            stock = "3.5",
            categoria = "cat"
        )

        assertEquals("El stock no es un número válido", errores["stock"])
    }

    @Test
    fun elStockNoPuedeSerNegativo() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "10",
            stock = "-2",
            categoria = "cat"
        )

        assertEquals("El stock no puede ser negativo", errores["stock"])
    }

    @Test
    fun laCategoriaEsObligatoria() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "10",
            stock = "1",
            categoria = " "
        )

        assertEquals("La categoría es obligatoria", errores["categoria"])
    }

    @Test
    fun laImagenEsObligatoriaSoloEnLaPublicacion() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "10",
            stock = "1",
            categoria = "cat",
            requiereImagen = true,
            imagenPresente = false
        )

        assertEquals("La imagen es obligatoria", errores["imagen"])
    }

    @Test
    fun laEdicionNoExigeImagen() {
        val errores = ValidadorPublicacion.validar(
            nombre = "Teclado",
            descripcion = "desc",
            precio = "10",
            stock = "1",
            categoria = "cat",
            requiereImagen = false,
            imagenPresente = false
        )

        assertFalse("La edición no valida la imagen", errores.containsKey("imagen"))
    }

    @Test
    fun seReportanTodosLosCamposInvalidos() {
        val errores = ValidadorPublicacion.validar(
            nombre = "",
            descripcion = "",
            precio = "-1",
            stock = "-1",
            categoria = "",
            requiereImagen = true,
            imagenPresente = false
        )

        assertEquals(6, errores.size)
        assertTrue(errores.containsKey("nombre"))
        assertTrue(errores.containsKey("descripcion"))
        assertTrue(errores.containsKey("precio"))
        assertTrue(errores.containsKey("stock"))
        assertTrue(errores.containsKey("categoria"))
        assertTrue(errores.containsKey("imagen"))
    }

    @Test
    fun lasExtensionesAdmitidasSonJpgJpegYPng() {
        assertTrue(ValidadorPublicacion.extensionPermitida("teclado.jpg"))
        assertTrue(ValidadorPublicacion.extensionPermitida("teclado.JPEG"))
        assertTrue(ValidadorPublicacion.extensionPermitida("teclado.png"))
        assertFalse(ValidadorPublicacion.extensionPermitida("teclado.gif"))
        assertFalse(ValidadorPublicacion.extensionPermitida("teclado.pdf"))
    }

    @Test
    fun losMimeAdmitidosSonLosDeImagen() {
        assertTrue(ValidadorPublicacion.mimePermitido("image/jpeg"))
        assertTrue(ValidadorPublicacion.mimePermitido("image/jpg"))
        assertTrue(ValidadorPublicacion.mimePermitido("image/png"))
        // Un MIME vacío se acepta: decide la extensión.
        assertTrue(ValidadorPublicacion.mimePermitido(""))
        assertFalse(ValidadorPublicacion.mimePermitido("application/pdf"))
    }
}
