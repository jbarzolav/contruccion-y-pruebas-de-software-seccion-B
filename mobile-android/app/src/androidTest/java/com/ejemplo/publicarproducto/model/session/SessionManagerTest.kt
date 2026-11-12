package com.ejemplo.publicarproducto.model.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica que SessionManager conserva su API y acepta un store sustituible. */
@RunWith(AndroidJUnit4::class)
class SessionManagerTest {

    @Test
    fun delegaGuardarLeerActualizarYLimpiarEnElStoreInyectado() {
        val contexto: Context = ApplicationProvider.getApplicationContext()
        val manager = SessionManager(contexto, SesionStoreEnMemoria())

        manager.guardarSesion(
            token = "token-original",
            id = 17L,
            correo = "ana@pulgatec.pe",
            rol = "CLIENTE",
            permisos = setOf("COMPRAR")
        )

        assertTrue(manager.haySesion())
        assertEquals("token-original", manager.obtenerToken())
        assertEquals(17L, manager.obtenerUsuarioId())
        assertEquals(
            UsuarioRolResponse(17L, "ana@pulgatec.pe", "CLIENTE", setOf("COMPRAR")),
            manager.obtenerUsuario()
        )

        manager.actualizarUsuario(
            UsuarioRolResponse(17L, "ana@pulgatec.pe", "VENDEDOR", setOf("GESTIONAR_PRODUCTOS"))
        )

        assertEquals("token-original", manager.obtenerToken())
        assertEquals("VENDEDOR", manager.obtenerUsuario()?.rol)
        assertEquals(setOf("GESTIONAR_PRODUCTOS"), manager.obtenerUsuario()?.permisos)

        manager.invalidarSesion()

        assertFalse(manager.haySesion())
        assertEquals(0L, manager.obtenerUsuarioId())
        assertNull(manager.obtenerUsuario())
    }

    @Test
    fun noActualizaUsuarioSiNoHayToken() {
        val contexto: Context = ApplicationProvider.getApplicationContext()
        val store = SesionStoreEnMemoria()
        val manager = SessionManager(contexto, store)
        val usuario = UsuarioRolResponse(9L, "usuario@pulgatec.pe", "VENDEDOR", emptySet())

        manager.actualizarUsuario(usuario)

        assertEquals(SesionGuardada(), store.leer())
    }
}
