package com.ejemplo.publicarproducto.model.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas instrumentadas del formato persistido y su compatibilidad. */
@RunWith(AndroidJUnit4::class)
class SesionStorePreferenciasTest {
    private val nombrePreferencias = "sesion_store_test"
    private lateinit var contexto: Context
    private lateinit var store: SesionStorePreferencias

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        contexto.getSharedPreferences(nombrePreferencias, Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = SesionStorePreferencias(
            contexto,
            nombrePreferencias,
            CifradorEnMemoria()
        )
    }

    @After
    fun tearDown() {
        store.limpiar()
    }

    @Test
    fun guardaElTokenCifradoYPuedeLeerlo() {
        store.guardar(
            SesionGuardada(
                token = "token-secreto",
                usuarioId = 8L,
                correo = "ana@pulgatec.pe",
                rol = "VENDEDOR",
                permisos = setOf("GESTIONAR_PRODUCTOS")
            )
        )

        val valorCrudo = store.valorCrudo("pulgatec_token")
        assertTrue(valorCrudo?.startsWith("v1:") == true)
        assertNotEquals("token-secreto", valorCrudo)
        assertEquals("token-secreto", store.leer().token)
        assertEquals(8L, store.leer().usuarioId)
    }

    @Test
    fun conservaCompatibilidadConUnTokenEnClaroDeUnaVersionAnterior() {
        contexto.getSharedPreferences(nombrePreferencias, Context.MODE_PRIVATE)
            .edit()
            .putString("pulgatec_token", "token-version-anterior")
            .putLong("pulgatec_usuario_id", 9L)
            .commit()

        assertEquals("token-version-anterior", store.leer().token)
        assertEquals(9L, store.leer().usuarioId)
    }

    @Test
    fun unTokenCifradoCorruptoSeTrataComoSesionAusente() {
        contexto.getSharedPreferences(nombrePreferencias, Context.MODE_PRIVATE)
            .edit()
            // No es Base64 válido: permite probar el rechazo sin fingir que
            // el cifrador XOR de pruebas ofrece autenticación criptográfica.
            .putString("pulgatec_token", "v1:corrupto!")
            .commit()

        assertNull(store.leer().token)
    }

    @Test
    fun guardarSesionSinTokenEliminaLaClave() {
        store.guardar(SesionGuardada(token = "token-secreto"))
        store.guardar(SesionGuardada(token = null, usuarioId = 0L))

        assertNull(store.valorCrudo("pulgatec_token"))
    }

    @Test
    fun siElCifradorNoEstaDisponibleFallaSinGuardarElTokenEnClaro() {
        val cifradorNoDisponible = object : CifradorToken {
            override fun cifrar(plano: String): String? = null
            override fun descifrar(cifrado: String): String? = null
        }
        val storeConRespaldo = SesionStorePreferencias(
            contexto,
            nombrePreferencias,
            cifradorNoDisponible
        )

        try {
            storeConRespaldo.guardar(SesionGuardada(token = "token-respaldo"))
            fail("No debe persistir un token sin poder cifrarlo")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("no se guardará en claro"))
        }
        assertNull(storeConRespaldo.valorCrudo("pulgatec_token"))
    }
}
