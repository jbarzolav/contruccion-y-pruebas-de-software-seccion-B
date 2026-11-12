package com.ejemplo.publicarproducto.model.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grupo D-3 - Integración de la edición de reseñas (HU 45).
 *
 * Combina MockWebServer con Room en memoria para verificar el ciclo
 * completo de `PedidosRepositorio.editarResena`: el backend confirma, la
 * copia local se actualiza, un error del servidor conserva la reseña
 * anterior y el ORDEN es siempre Retrofit primero, Room después.
 *
 * No usa backend real ni base de datos del dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class PedidosRepositorioResenaTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var servidor: ServidorDePruebas
    private lateinit var cola: ColaOperacionesRepositorio
    private lateinit var repositorio: PedidosRepositorio

    private val usuarioId = 1L

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        servidor = ServidorDePruebas()
        cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        repositorio = PedidosRepositorio(bd, servidor.api, cola)
    }

    @After
    fun tearDown() {
        bd.close()
        servidor.cerrar()
    }

    private val jsonPedidosConResena = """
        [
          {
            "idPedido": 55,
            "clienteId": 1,
            "fecha": "2024-05-01T10:15:30",
            "total": 120.00,
            "estado": "REGISTRADO",
            "fechaActualizacion": "2024-05-01T10:15:30",
            "detalles": [
              {"idProducto": 900, "nombreProducto": "Teclado", "cantidad": 2,
               "precioUnitario": 60.00, "subtotal": 120.00}
            ],
            "calificacion": null,
            "resenas": [
              {"id": 9, "idPedido": 55, "destino": "PRODUCTO", "productoId": 900,
               "vendedorId": 8, "texto": "Buen producto",
               "fecha": "2024-05-01T11:00:00",
               "fechaModificacion": "2024-05-01T11:00:00"}
            ]
          }
        ]
    """.trimIndent()

    private val jsonResenaActualizada = """
        {"id":9,"idPedido":55,"destino":"PRODUCTO","productoId":900,
         "vendedorId":8,"texto":"Ahora excelente",
         "fecha":"2024-05-01T11:00:00",
         "fechaModificacion":"2024-05-03T08:30:00"}
    """.trimIndent()

    private suspend fun textoEnRoom(): String? =
        repositorio.observarCompras(usuarioId).first().first().resenas?.firstOrNull()?.texto

    @Test
    fun laEdicionExitosaActualizaLaCopiaLocal() = runBlocking {
        servidor.responder(200, jsonPedidosConResena)
        repositorio.refrescarCompras(usuarioId)
        assertEquals("Buen producto", textoEnRoom())

        servidor.responder(200, jsonResenaActualizada)
        val resultado = repositorio.editarResena(usuarioId, idPedido = 55, idResena = 9, texto = "Ahora excelente")

        assertTrue("Debería editar: $resultado", resultado is ResultadoApi.Exito)
        assertEquals("Ahora excelente", textoEnRoom())

        val pedidos = repositorio.observarCompras(usuarioId).first()
        val resena = pedidos.first().resenas!!.first()
        assertEquals(9L, resena.id)
        assertEquals(55L, resena.idPedido)
        // La copia local guarda las fechas normalizadas al formato fijo.
        assertEquals("2024-05-03T08:30:00.000000000", resena.fechaModificacion)

        // Contrato HTTP: PUT /api/resenas/9 con el texto.
        val peticion = servidor.peticiones().first { it.method == "PUT" }
        assertEquals("/api/resenas/9", peticion.path)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar el texto: $cuerpo", cuerpo.contains("\"texto\":\"Ahora excelente\""))
    }

    @Test
    fun unErrorDelBackendConservaLaResenaAnterior() = runBlocking {
        servidor.responder(200, jsonPedidosConResena)
        repositorio.refrescarCompras(usuarioId)

        servidor.responderError(400, "La reseña no existe")
        val resultado = repositorio.editarResena(usuarioId, idPedido = 55, idResena = 9, texto = "Nuevo")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "La reseña no existe",
            (resultado as ResultadoApi.Error).mensaje
        )
        // Room no se toca: la reseña original se conserva.
        assertEquals("Buen producto", textoEnRoom())
    }

    @Test
    fun sinConexionLaResenaLocalSeConserva() = runBlocking {
        servidor.responder(200, jsonPedidosConResena)
        repositorio.refrescarCompras(usuarioId)

        servidor.apagar()
        val resultado = repositorio.editarResena(usuarioId, idPedido = 55, idResena = 9, texto = "Nuevo")

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertTrue("Mensaje crudo: ${error.mensaje}", error.mensaje.isNotBlank())
        assertEquals("Buen producto", textoEnRoom())
    }

    @Test
    fun elOrdenEsPrimeroRetrofitDespuesRoom() = runBlocking {
        servidor.responder(200, jsonPedidosConResena)
        repositorio.refrescarCompras(usuarioId)

        // Cuando el servidor recibe el PUT, Room todavía NO fue escrito.
        var textoEnRoomAlResponder: String? = null
        servidor.servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                textoEnRoomAlResponder = runBlocking { textoEnRoom() }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(jsonResenaActualizada)
            }
        }

        val resultado = repositorio.editarResena(usuarioId, idPedido = 55, idResena = 9, texto = "Ahora excelente")

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals(
            "Room debe escribirse DESPUÉS de la respuesta HTTP",
            "Buen producto",
            textoEnRoomAlResponder
        )
        // Tras la respuesta, la escritura local sí quedó reflejada.
        assertEquals("Ahora excelente", textoEnRoom())
    }
}
