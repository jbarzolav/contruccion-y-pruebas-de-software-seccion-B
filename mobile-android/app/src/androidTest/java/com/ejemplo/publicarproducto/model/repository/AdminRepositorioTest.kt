package com.ejemplo.publicarproducto.model.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grupo A - AdminRepositorio: HU 23 (roles) y HU 24 (auditoría).
 *
 * Estos endpoints los sirve Django REST Framework y NO comparten contrato
 * con AuthRepositorio: se comprueba que la ruta, el método y los mensajes
 * de error siguen siendo los mismos que antes de la refactorización.
 */
@RunWith(AndroidJUnit4::class)
class AdminRepositorioTest {

    private lateinit var servidor: ServidorDePruebas
    private lateinit var admin: AdminRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebas()
        admin = AdminRepositorio(servidor.adminApi)
    }

    @After
    fun tearDown() {
        servidor.cerrar()
    }

    @Test
    fun listarUsuariosDevuelveLaListaDelBackend() = runBlocking {
        servidor.responder(
            200,
            """[{"id":3,"correo":"ana@pulgatec.pe","rol":"CLIENTE","permisos":["COMPRAR"]}]"""
        )

        val resultado = admin.listarUsuarios()

        assertTrue("Debería listar: $resultado", resultado is ResultadoApi.Exito)
        val usuarios = (resultado as ResultadoApi.Exito).valor
        assertEquals(1, usuarios.size)
        assertEquals(3L, usuarios[0].id)
        assertEquals(setOf("COMPRAR"), usuarios[0].permisos)
        // El contrato con Django no cambia.
        assertEquals("/api/admin/usuarios", servidor.ultimaRuta())
    }

    @Test
    fun listarUsuariosSinConexionConservaElMensajePropio() = runBlocking {
        servidor.apagar()

        val resultado = admin.listarUsuarios()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No se pudieron cargar los usuarios.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun asignarRolUsaElPatchDeDjangoYMuestraElMensajeDe403() = runBlocking {
        servidor.responderError(403, "No tienes permiso para asignar roles")

        val resultado = admin.asignarRol(3L, "VENDEDOR")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No tienes permiso para asignar roles",
            (resultado as ResultadoApi.Error).mensaje
        )
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/admin/usuarios/3/rol", peticion.path)
        assertEquals("PATCH", peticion.method)
        assertTrue(peticion.body.readUtf8().contains("\"rol\":\"VENDEDOR\""))
    }

    @Test
    fun listarAuditoria401UsaElMensajeDeSesionCuandoNoHayMessages() = runBlocking {
        // Django devuelve `detail`, no `messages`: se conserva el texto propio.
        servidor.responder(401, """{"detail":"Autenticación no proveída."}""")

        val resultado = admin.listarAuditoria(0, 20)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Inicia sesión para consultar la auditoría.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun listarAuditoriaConservaLaPaginacion() = runBlocking {
        servidor.responder(
            200,
            """{"contenido":[{"id":1,"accion":"LOGIN","usuario":"ana@pulgatec.pe","fecha":"2026-01-01T10:00:00"}],
                "pagina":2,"tamano":20,"totalElementos":41,"totalPaginas":3}"""
        )

        val resultado = admin.listarAuditoria(2, 20)

        assertTrue(resultado is ResultadoApi.Exito)
        val pagina = (resultado as ResultadoApi.Exito).valor
        assertEquals(2, pagina.pagina)
        assertEquals(41L, pagina.totalElementos)
        assertEquals(1, pagina.contenido.size)
        val peticion = servidor.ultimaPeticion()
        assertTrue("page=2", peticion.path?.contains("page=2") == true)
        assertTrue("size=20", peticion.path?.contains("size=20") == true)
    }

    @Test
    fun listarAuditoria403UsaElMensajeDePermiso() = runBlocking {
        // Sin `messages` en el cuerpo se conserva el texto propio del código.
        servidor.responder(403, """{"detail":"No autorizado."}""")

        val resultado = admin.listarAuditoria(0, 20)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No tienes permiso para consultar la auditoría.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }
}
