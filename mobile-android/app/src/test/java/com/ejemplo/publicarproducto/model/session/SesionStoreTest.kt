package com.ejemplo.publicarproducto.model.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas JVM puras del store y del cifrador sustituible en memoria. */
class SesionStoreTest {

    @Test
    fun elStoreEnMemoriaGuardaYLeeLaSesion() {
        val store = SesionStoreEnMemoria()
        val sesion = SesionGuardada(
            token = "token-prueba",
            usuarioId = 42L,
            correo = "ana@pulgatec.pe",
            rol = "VENDEDOR",
            permisos = setOf("GESTIONAR_PRODUCTOS", "COMPRAR")
        )

        store.guardar(sesion)

        assertEquals(sesion, store.leer())
    }

    @Test
    fun elStoreEnMemoriaLimpiaLaSesion() {
        val store = SesionStoreEnMemoria()
        store.guardar(SesionGuardada(token = "token-prueba", usuarioId = 42L))

        store.limpiar()

        assertEquals(SesionGuardada(), store.leer())
    }

    @Test
    fun elCifradorEnMemoriaHaceRoundTrip() {
        val cifrador = CifradorEnMemoria()

        val cifrado = cifrador.cifrar("token-prueba")

        assertNotEquals("token-prueba", cifrado)
        assertEquals("token-prueba", cifrador.descifrar(cifrado))
    }

    @Test
    fun elCifradorEnMemoriaDevuelveNullSiElTextoNoEsValido() {
        assertNull(CifradorEnMemoria().descifrar("no-es-un-criptograma-valido"))
    }
}
