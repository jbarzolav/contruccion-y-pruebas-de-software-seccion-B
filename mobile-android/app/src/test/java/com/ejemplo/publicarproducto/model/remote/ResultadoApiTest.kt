package com.ejemplo.publicarproducto.model.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

/** Pruebas JVM de clasificación HTTP, cancelación y expiración de sesión. */
class ResultadoApiTest {

    private fun <T> error(codigo: Int): Response<T> =
        Response.error(codigo, "{}".toResponseBody("application/json".toMediaType()))

    @Test
    fun laCancelacionSePropagaEnLaSobrecargaBasica() = runBlocking {
        try {
            ejecutarPeticion<String> { throw CancellationException("cancelado") }
            fail("La cancelación debe propagarse")
        } catch (e: CancellationException) {
            assertEquals("cancelado", e.message)
        }
    }

    @Test
    fun laCancelacionSePropagaEnLaSobrecargaDeMensajes() = runBlocking {
        try {
            ejecutarPeticion<String>(
                excepcion = { "error de red" },
                porCodigo = { "error HTTP $it" }
            ) { throw CancellationException("cancelado") }
            fail("La cancelación debe propagarse")
        } catch (e: CancellationException) {
            assertEquals("cancelado", e.message)
        }
    }

    @Test
    fun laCancelacionSePropagaEnLaSobrecargaDeSesion() = runBlocking {
        try {
            ejecutarPeticion<String>(
                sinRed = "sin red",
                fallo = "fallo",
                porCodigo = { "error HTTP $it" }
            ) { throw CancellationException("cancelado") }
            fail("La cancelación debe propagarse")
        } catch (e: CancellationException) {
            assertEquals("cancelado", e.message)
        }
    }

    @Test
    fun un401MarcaLaSesionComoExpiradaEnLaSobrecargaDeSesion() = runBlocking {
        val resultado = ejecutarPeticion<String>(
            sinRed = "sin red",
            fallo = "fallo",
            porCodigo = { "error HTTP $it" }
        ) { error(401) }

        assertTrue(resultado is ResultadoApi.Error)
        resultado as ResultadoApi.Error
        assertTrue(resultado.sesionExpirada)
        assertFalse(resultado.temporal)
    }

    @Test
    fun un500EsTemporalYNoEsSesionExpiradaEnLaSobrecargaDeSesion() = runBlocking {
        val resultado = ejecutarPeticion<String>(
            sinRed = "sin red",
            fallo = "fallo",
            porCodigo = { "error HTTP $it" }
        ) { error(500) }

        assertTrue(resultado is ResultadoApi.Error)
        resultado as ResultadoApi.Error
        assertTrue(resultado.temporal)
        assertFalse(resultado.sesionExpirada)
    }

    @Test
    fun un408YUn429SonTemporales() = runBlocking {
        for (codigo in listOf(408, 429)) {
            val resultado = ejecutarPeticion<String>(
                sinRed = "sin red",
                fallo = "fallo",
                porCodigo = { "error HTTP $it" }
            ) { error(codigo) }

            assertTrue("HTTP $codigo debe ser temporal", resultado is ResultadoApi.Error)
            assertTrue("HTTP $codigo debe ser temporal", (resultado as ResultadoApi.Error).temporal)
        }
    }

    @Test
    fun un400EsRechazoDefinitivo() = runBlocking {
        val resultado = ejecutarPeticion<String>(
            sinRed = "sin red",
            fallo = "fallo",
            porCodigo = { "error HTTP $it" }
        ) { error(400) }

        assertTrue(resultado is ResultadoApi.Error)
        resultado as ResultadoApi.Error
        assertFalse(resultado.temporal)
        assertFalse(resultado.sesionExpirada)
    }

    @Test
    fun un401MarcaLaSesionComoExpiradaEnLaSobrecargaBasica() = runBlocking {
        val resultado = ejecutarPeticion<String> { error(401) }

        assertTrue(resultado is ResultadoApi.Error)
        assertTrue((resultado as ResultadoApi.Error).sesionExpirada)
    }

    @Test
    fun unExitoDevuelveElCuerpo() = runBlocking {
        val resultado = ejecutarPeticion { Response.success("respuesta") }

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals("respuesta", (resultado as ResultadoApi.Exito).valor)
    }

    @Test
    fun unaExcepcionDeRedNoEsSesionExpiradaYEsTemporal() = runBlocking {
        val resultado = ejecutarPeticion<String> { throw java.io.IOException("sin conexión") }

        assertTrue(resultado is ResultadoApi.Error)
        resultado as ResultadoApi.Error
        assertEquals(null, resultado.codigo)
        assertTrue(resultado.temporal)
        assertFalse(resultado.sesionExpirada)
    }
}
