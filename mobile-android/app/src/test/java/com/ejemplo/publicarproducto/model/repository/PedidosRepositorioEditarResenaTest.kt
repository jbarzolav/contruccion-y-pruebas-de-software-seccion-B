package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebasUnitario
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo D-3 - Edición de reseñas de PedidosRepositorio (HU 45).
 *
 * Pruebas unitarias (JVM, sin emulador) contra un servidor HTTP real de
 * pruebas: verifican el contrato `PUT api/resenas/{idResena}`, el ORDEN
 * Retrofit → Room, que un fallo del backend NO toca la copia local y los
 * textos de error EXACTOS que mostraba la pantalla:
 * `"Error N al consultar pedidos."`, el primer `messages[]` y
 * `"No se pudo actualizar la reseña."`.
 *
 * Room no existe en la JVM: la escritura local se inyecta como
 * `guardarLocal` (en producción es `actualizarResenaLocal`), lo que
 * permite comprobar también el orden y el comportamiento ante un fallo
 * local sin base de datos real.
 */
class PedidosRepositorioEditarResenaTest {

    private lateinit var servidor: ServidorDePruebasUnitario

    @Before
    fun setUp() {
        servidor = ServidorDePruebasUnitario()
    }

    @After
    fun tearDown() {
        servidor.cerrar()
    }

    private val cuerpoResena = """
        {"id":9,"idPedido":55,"destino":"PRODUCTO","productoId":900,
         "vendedorId":8,"texto":"Ahora excelente","fecha":"2024-05-01T11:00:00",
         "fechaModificacion":"2024-05-03T08:30:00"}
    """.trimIndent()

    // ------------------------------------------------------------ éxito
    @Test
    fun laEdicionExitosaUsaElPutDeLaResena() = runBlocking {
        servidor.responder(200, cuerpoResena)

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Ahora excelente") { }

        assertTrue("Debería editar: $resultado", resultado is ResultadoApi.Exito)
        val resena = (resultado as ResultadoApi.Exito).valor
        assertEquals(9L, resena.id)
        assertEquals(55L, resena.idPedido)
        assertEquals("Ahora excelente", resena.texto)

        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/resenas/9", peticion.path)
        assertEquals("PUT", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar el texto: $cuerpo", cuerpo.contains("\"texto\":\"Ahora excelente\""))
    }

    @Test
    fun laRespuestaConfirmadaSeEntregaALaEscrituraLocal() = runBlocking {
        servidor.responder(200, cuerpoResena)
        var guardada: String? = null

        editarResenaConApi(servidor.api, idResena = 9, texto = "Ahora excelente") { resena ->
            guardada = resena.texto
        }

        assertEquals("Ahora excelente", guardada)
    }

    @Test
    fun elOrdenEsPrimeroRetrofitDespuesRoom() = runBlocking {
        servidor.responder(200, cuerpoResena)
        var peticionesAlEscribir = -1

        editarResenaConApi(servidor.api, idResena = 9, texto = "Ahora excelente") {
            // La petición HTTP ya llegó al servidor ANTES de escribir en Room.
            peticionesAlEscribir = servidor.peticionesRecibidas()
        }

        assertEquals("Retrofit debe ir primero: $peticionesAlEscribir", 1, peticionesAlEscribir)
    }

    // ------------------------------------------------------- errores HTTP
    @Test
    fun unErrorSinMessagesUsaElTextoPorCodigo() = runBlocking {
        servidor.responder(400, "{}")
        var escribioLocal = false

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") { escribioLocal = true }

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(400, error.codigo)
        assertEquals("Error 400 al consultar pedidos.", error.mensaje)
        assertFalse("Un fallo del backend no debe escribir en Room", escribioLocal)
    }

    @Test
    fun unErrorConMessagesUsaElPrimerMensaje() = runBlocking {
        servidor.responderError(400, "La reseña no existe")
        var escribioLocal = false

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") { escribioLocal = true }

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals("La reseña no existe", (resultado as ResultadoApi.Error).mensaje)
        assertFalse(escribioLocal)
    }

    @Test
    fun unaListaDeMessagesVaciaUsaElTextoPorCodigo() = runBlocking {
        servidor.responder(400, """{"status":400,"error":"Validación fallida","messages":[]}""")

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") { }

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals("Error 400 al consultar pedidos.", (resultado as ResultadoApi.Error).mensaje)
    }

    @Test
    fun unMensajeVacioDeMessagesSeConservaTalCual() = runBlocking {
        // Comportamiento original: `messages.firstOrNull()` no es null, así que
        // el texto vacío se muestra tal cual (sin caer al mensaje por código).
        servidor.responder(400, """{"status":400,"error":"x","messages":[""]}""")

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") { }

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals("", (resultado as ResultadoApi.Error).mensaje)
    }

    @Test
    fun unError500EsTemporal() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") { }

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals("Error 500 al consultar pedidos.", error.mensaje)
        assertTrue("Un 5xx es temporal: ${error.temporal}", error.temporal)
    }

    @Test
    fun un200ConCuerpoNuloUsaElTextoPorCodigo() = runBlocking {
        // Cuerpo "null" → Retrofit devuelve 2xx sin objeto: mismo camino que
        // el original (`isSuccessful && body() != null` → mensajeDeError).
        servidor.responder(200, "null")
        var escribioLocal = false

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") { escribioLocal = true }

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals("Error 200 al consultar pedidos.", error.mensaje)
        assertFalse("Sin confirmación del servidor no se escribe en Room", escribioLocal)
    }

    @Test
    fun un200ConCuerpoVacioSeInformaComoExcepcionSinPrefijo() = runBlocking {
        // Un cuerpo vacío hace fallar a Gson DURANTE la llamada Retrofit
        // (EOFException). El código original también mostraba `e.message`
        // tal cual en ese caso: sin prefijo "Error " y sin escribir en Room.
        servidor.responder(200, "")
        var escribioLocal = false

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") { escribioLocal = true }

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue("Mensaje crudo: ${error.mensaje}", error.mensaje.isNotBlank())
        assertFalse(error.mensaje.startsWith("Error "))
        assertFalse("Sin confirmación del servidor no se escribe en Room", escribioLocal)
    }

    @Test
    fun sinConexionConservaElMensajeCrudoDeLaExcepcion() = runBlocking {
        servidor.apagar()

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") { }

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue(error.temporal)
        assertTrue("Mensaje crudo: ${error.mensaje}", error.mensaje.isNotBlank())
        assertFalse(error.mensaje.startsWith("Error "))
        assertFalse(error.mensaje.startsWith("No hay conexión"))
    }

    // ------------------------------------------------- fallo de Room (local)
    @Test
    fun unFalloLocalSeInformaSinOcultarElError() = runBlocking {
        servidor.responder(200, cuerpoResena)

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") {
            error("Room dijo no")
        }

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertEquals("Room dijo no", error.mensaje)
    }

    @Test
    fun unFalloLocalSinMensajeUsaElTextoPorDefinido() = runBlocking {
        servidor.responder(200, cuerpoResena)

        val resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") {
            throw RuntimeException()
        }

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No se pudo actualizar la reseña.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ---------------------------------------------------------- cancelación
    @Test
    fun laCancelacionSePropagaYNoSeConvierteEnResultadoDeError() = runBlocking {
        servidor.servidor.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        var resultado: ResultadoApi<*>? = null
        var fallo: Throwable? = null
        var escribioLocal = false

        val trabajo = launch {
            try {
                resultado = editarResenaConApi(servidor.api, idResena = 9, texto = "Nuevo") {
                    escribioLocal = true
                }
            } catch (e: Throwable) {
                fallo = e
            }
        }

        val limite = System.currentTimeMillis() + 3_000L
        while (servidor.peticionesRecibidas() == 0 && System.currentTimeMillis() < limite) {
            delay(50)
        }
        delay(200)

        trabajo.cancel()
        trabajo.join()

        assertNull(
            "La cancelación no debe convertirse en ResultadoApi: $resultado",
            resultado
        )
        assertTrue(
            "Debe propagarse como CancellationException: $fallo",
            fallo is CancellationException
        )
        assertFalse("No debe escribir en Room tras cancelar", escribioLocal)
    }
}
