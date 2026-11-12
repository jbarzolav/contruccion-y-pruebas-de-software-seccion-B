package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo B - FavoritosRepositorio en el dispositivo (HU 31, HU 33 y HU 35).
 *
 * Comprueba en el runtime real del emulador que los endpoints de favoritos
 * y sus mensajes de error no cambian.
 */
class FavoritosRepositorioTest {

    private lateinit var servidor: ServidorDePruebas
    private lateinit var repositorio: FavoritosRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebas()
        repositorio = FavoritosRepositorio(servidor.api)
    }

    @After
    fun tearDown() {
        servidor.cerrar()
    }

    @Test
    fun listarFavoritosUsaLaRutaDelUsuario() = runBlocking {
        servidor.responder(200, "[]")

        val resultado = repositorio.listarFavoritos()

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals("/api/usuarios/me/favoritos", servidor.ultimaRuta())
    }

    @Test
    fun listarFavoritosConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.listarFavoritos()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(500, error.codigo)
        assertEquals("Error 500: no se pudieron cargar tus favoritos.", error.mensaje)
    }

    @Test
    fun sinConexionElMensajeEsElDeFavoritos() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.listarFavoritos()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertEquals("No hay conexión para cargar tus favoritos.", error.mensaje)
    }

    @Test
    fun consultarEstadoFavoritoUsaLaRutaDeEstado() = runBlocking {
        servidor.responder(200, """{"productoId":7,"favorito":true}""")

        val resultado = repositorio.consultarEstadoFavorito(7)

        assertTrue(resultado is ResultadoApi.Exito)
        assertTrue((resultado as ResultadoApi.Exito).valor.favorito)
        assertEquals("/api/usuarios/me/favoritos/7/estado", servidor.ultimaRuta())
    }

    @Test
    fun agregarFavoritoUsaElPostDelUsuario() = runBlocking {
        servidor.responder(200, """{"productoId":7,"favorito":true}""")

        val resultado = repositorio.agregarFavorito(7)

        assertTrue("Debería guardar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/usuarios/me/favoritos/7", peticion.path)
        assertEquals("POST", peticion.method)
    }

    @Test
    fun agregarFavoritoSinConfirmarConservaElMensajeOriginal() = runBlocking {
        servidor.responder(200, """{"productoId":7,"favorito":false}""")

        val resultado = repositorio.agregarFavorito(7)

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(200, error.codigo)
        assertEquals("Error 200: no se pudo guardar el favorito.", error.mensaje)
    }

    @Test
    fun quitarFavoritoUsaElDeleteDelUsuario() = runBlocking {
        servidor.responder(204, "")

        val resultado = repositorio.quitarFavorito(7)

        assertTrue("Debería quitar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/usuarios/me/favoritos/7", peticion.path)
        assertEquals("DELETE", peticion.method)
    }

    @Test
    fun quitarFavoritoInexistenteConservaElCodigo404() = runBlocking {
        servidor.responder(404, "{}")

        val resultado = repositorio.quitarFavorito(7)

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(404, error.codigo)
        assertEquals("Error 404: no se pudo quitar el favorito.", error.mensaje)
    }
}
