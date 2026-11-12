package com.ejemplo.publicarproducto.model.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.ActualizarPerfilRequest
import com.ejemplo.publicarproducto.model.RegistroRequest
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grupo A - AuthRepositorio: login, registro, sesión, recuperación y perfil.
 *
 * Verifica contra el servidor local de pruebas que:
 * - el repositorio es el único que persiste la sesión;
 * - los textos de error son exactamente los de la implementación anterior;
 * - los contratos HTTP (ruta, método, multipart) no cambian.
 */
@RunWith(AndroidJUnit4::class)
class AuthRepositorioTest {

    private lateinit var contexto: Context
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas
    private lateinit var auth: AuthRepositorio

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        sesion = SessionManager(contexto)
        sesion.invalidarSesion()
        servidor = ServidorDePruebas()
        auth = AuthRepositorio(servidor.authApi, sesion)
    }

    @After
    fun tearDown() {
        sesion.invalidarSesion()
        servidor.cerrar()
    }

    @Test
    fun loginExitosoPersisteTokenIdentidadYPermisos() = runBlocking {
        servidor.responder(
            200,
            """{"token":"tok-abc","id":77,"correo":"ana@pulgatec.pe","nombre":"Ana","rol":"CLIENTE","permisos":["COMPRAR"]}"""
        )

        val resultado = auth.iniciarSesion("ana@pulgatec.pe", "secreta123")

        assertTrue("Debería iniciar sesión: $resultado", resultado is ResultadoApi.Exito)
        assertEquals("tok-abc", sesion.obtenerToken())
        assertEquals(77L, sesion.obtenerUsuarioId())
        assertEquals("ana@pulgatec.pe", sesion.obtenerUsuario()?.correo)
        assertEquals(setOf("COMPRAR"), sesion.obtenerUsuario()?.permisos)
        // El contrato HTTP no cambia: sigue siendo POST /api/auth/login.
        assertEquals("/api/auth/login", servidor.ultimaRuta())
    }

    @Test
    fun loginRechazadoNoRevelaSiElCorreoExiste() = runBlocking {
        servidor.responderError(401, "Usuario no encontrado")

        val resultado = auth.iniciarSesion("noexiste@pulgatec.pe", "otracosa1")

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(401, error.codigo)
        assertEquals("Correo o contraseña incorrectos.", error.mensaje)
        assertFalse("Un login fallido no crea sesión", sesion.haySesion())
    }

    @Test
    fun login400LeeLosMensajesDeValidacionDelBackend() = runBlocking {
        servidor.responderError(400, "El correo no tiene un formato válido")

        val resultado = auth.iniciarSesion("mal-correo", "secreta123")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "El correo no tiene un formato válido",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun loginSinConexionEsUnErrorTemporalConElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = auth.iniciarSesion("ana@pulgatec.pe", "secreta123")

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue("Sin red el error debe ser temporal", error.temporal)
        assertEquals("No hay conexión con el servidor.", error.mensaje)
    }

    @Test
    fun obtenerSesionNoPersisteLaIdentidadPorSiSolo() = runBlocking {
        assertFalse(sesion.haySesion())
        servidor.responder(
            200,
            """{"id":9,"correo":"vendedor@pulgatec.pe","rol":"VENDEDOR","permisos":["GESTIONAR_PRODUCTOS"]}"""
        )

        val resultado = auth.obtenerSesion()

        assertTrue(resultado is ResultadoApi.Exito)
        // La identidad local la decide el ViewModel tras la guarda de token.
        assertFalse("obtenerSesion no debe escribir la sesión", sesion.haySesion())
        assertEquals("/api/auth/me", servidor.ultimaRuta())
    }

    @Test
    fun invalidarSesionDevuelveElIdentificadorAntesDeBorrar() {
        sesion.guardarSesion(token = "token-viejo", id = 4242L, correo = "ana@pulgatec.pe")

        val usuarioId = auth.invalidarSesion()

        assertEquals(4242L, usuarioId)
        assertFalse(sesion.haySesion())
        assertNull(sesion.obtenerToken())
    }

    @Test
    fun cerrarSesionSinConexionConservaElMensajeDeCierre() = runBlocking {
        servidor.apagar()

        val resultado = auth.cerrarSesion()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No se pudo confirmar el cierre en el servidor; se cerró la sesión local.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun registroDuplicadoMuestraElMensajeDelBackend() = runBlocking {
        servidor.responderError(409, "Ese correo ya está registrado")

        val resultado = auth.registrar(
            RegistroRequest(
                nombre = "Ana",
                correo = "ana@pulgatec.pe",
                password = "secreta123",
                confirmacionPassword = "secreta123"
            )
        )

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Ese correo ya está registrado",
            (resultado as ResultadoApi.Error).mensaje
        )
        assertEquals("/api/auth/registro", servidor.ultimaRuta())
    }

    @Test
    fun subirFotoUsaElMultipartEnLaRutaEsperada() = runBlocking {
        servidor.responder(
            200,
            """{"id":1,"correo":"vendedor@pulgatec.pe","rol":"VENDEDOR","permisos":[]}"""
        )

        val resultado = auth.subirFotoPerfil("contenido-de-imagen".toByteArray(), "jpg")

        assertTrue("Debería subir la foto: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/usuarios/me/foto", peticion.path)
        assertEquals("POST", peticion.method)
        assertTrue(
            "Debe enviarse como multipart",
            peticion.getHeader("Content-Type")?.contains("multipart/form-data") == true
        )
        assertTrue(peticion.body.readUtf8().contains("foto-perfil.jpg"))
    }

    @Test
    fun actualizarPerfil409ConservaElCodigoParaLaInterfaz() = runBlocking {
        servidor.responderError(409, "Correo duplicado")

        val resultado = auth.actualizarPerfil(
            ActualizarPerfilRequest(
                nombre = "Ana",
                correo = "ana@pulgatec.pe",
                telefono = null
            )
        )

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(409, error.codigo)
        assertFalse("Un rechazo del servidor no es temporal", error.temporal)
        // Los endpoints de perfil NO leían messages[]: conserva el texto propio.
        assertEquals("No se pudo actualizar el perfil (HTTP 409).", error.mensaje)
    }
}
