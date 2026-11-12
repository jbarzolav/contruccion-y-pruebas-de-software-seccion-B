package com.ejemplo.publicarproducto.model.session

import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 - Identidad reactiva de la sesión.
 *
 * `SessionManager` expone `usuarioIdFlow`: la ÚNICA fuente de la identidad
 * activa. Los ViewModels que observan datos protegidos (pedidos, avisos,
 * métricas, umbrales) se suscriben a ese flujo, de modo que al cerrar sesión
 * y entrar con otra cuenta en la misma `Activity` se cancelan los
 * collectors del usuario anterior y se observan los del nuevo.
 *
 * Antes, la identidad se capturaba en el `init` del ViewModel y quedaba
 * congelada durante toda la vida de la pantalla.
 *
 * Estas pruebas son JVM puras: usan `SesionStoreEnMemoria`, así que no
 * dependen del emulador ni de la cifrado real en `SharedPreferences`.
 */
class SessionManagerUsuarioReactivoTest {

    @Test
    fun elFlujoPublicaLaIdentidadGuardadaEnElStore() {
        val store = SesionStoreEnMemoria()
        store.guardar(SesionGuardada(token = "token", usuarioId = 17L))

        val manager = SessionManager(store)

        assertEquals(
            "El flujo debe arrancar con la identidad que ya estaba persistida",
            17L,
            manager.usuarioIdFlow.value
        )
    }

    @Test
    fun elFlujoArrancaSinUsuarioCuandoNoHaySesion() {
        val manager = SessionManager(SesionStoreEnMemoria())

        assertEquals(0L, manager.usuarioIdFlow.value)
        assertFalse(manager.haySesion())
        assertNull(manager.obtenerUsuario())
    }

    @Test
    fun guardarSesionPublicaElNuevoUsuario() {
        val manager = SessionManager(SesionStoreEnMemoria())

        manager.guardarSesion(
            token = "token-nuevo",
            id = 42L,
            correo = "ana@pulgatec.pe",
            rol = "VENDEDOR",
            permisos = setOf("GESTIONAR_PRODUCTOS")
        )

        assertEquals(42L, manager.usuarioIdFlow.value)
        assertEquals(42L, manager.obtenerUsuarioId())
        assertTrue(manager.haySesion())
        assertEquals("VENDEDOR", manager.obtenerUsuario()?.rol)
    }

    @Test
    fun actualizarUsuarioPublicaElNuevoUsuario() {
        val manager = SessionManager(SesionStoreEnMemoria())
        manager.guardarSesion(token = "token", id = 42L, correo = "ana@pulgatec.pe")

        manager.actualizarUsuario(
            UsuarioRolResponse(
                id = 99L,
                correo = "nuevo@pulgatec.pe",
                rol = "ADMIN",
                permisos = setOf("ASIGNAR_ROLES")
            )
        )

        assertEquals(
            "La identidad reactiva debe seguir a la sesión persistida",
            99L,
            manager.usuarioIdFlow.value
        )
        assertEquals(99L, manager.obtenerUsuarioId())
    }

    @Test
    fun invalidarSesionPublicaCero() {
        val manager = SessionManager(SesionStoreEnMemoria())
        manager.guardarSesion(token = "token", id = 42L, correo = "ana@pulgatec.pe")

        manager.invalidarSesion()

        assertEquals(
            "Sin sesión no hay usuario: el flujo debe publicar 0",
            0L,
            manager.usuarioIdFlow.value
        )
        assertFalse(manager.haySesion())
        assertNull(manager.obtenerUsuario())
    }

    @Test
    fun elFlujoEmiteSoloCuandoCambiaLaIdentidad() = runTest {
        val manager = SessionManager(SesionStoreEnMemoria())
        val emissions = mutableListOf<Long>()

        val colector = CoroutineScope(Dispatchers.Unconfined).launch {
            manager.usuarioIdFlow.toList(emissions)
        }

        manager.guardarSesion(token = "token", id = 42L, correo = "ana@pulgatec.pe")
        manager.guardarSesion(token = "otro-token", id = 42L, correo = "ana@pulgatec.pe")
        manager.actualizarUsuario(UsuarioRolResponse(42L, "ana@pulgatec.pe", "CLIENTE", emptySet()))
        manager.invalidarSesion()

        colector.cancel()
        yield()

        assertEquals(
            "El flujo debe publicar la identidad actual y solo los cambios reales",
            listOf(0L, 42L, 0L),
            emissions
        )
    }

    @Test
    fun elCierreDeSesionYLaEntradaDeOtroUsuarioSeReflejanEnElFlujo() = runTest {
        val manager = SessionManager(SesionStoreEnMemoria())
        val emissions = mutableListOf<Long>()

        val colector = CoroutineScope(Dispatchers.Unconfined).launch {
            manager.usuarioIdFlow.toList(emissions)
        }

        // Usuario A.
        manager.guardarSesion(token = "token-a", id = 1L, correo = "a@pulgatec.pe")
        // Cierre de sesión.
        manager.invalidarSesion()
        // Usuario B en la misma Activity.
        manager.guardarSesion(token = "token-b", id = 2L, correo = "b@pulgatec.pe")

        colector.cancel()
        yield()

        assertEquals(listOf(0L, 1L, 0L, 2L), emissions)
        assertEquals(2L, manager.usuarioIdFlow.value)
    }
}
