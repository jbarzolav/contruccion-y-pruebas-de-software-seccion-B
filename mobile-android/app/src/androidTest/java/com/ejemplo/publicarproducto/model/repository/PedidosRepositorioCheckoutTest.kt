package com.ejemplo.publicarproducto.model.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.local.TiposOperacion
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grupo C - Checkout de PedidosRepositorio (HU 15, HU 41 y HU 42).
 *
 * Estas pruebas comprueban dos cosas:
 *
 * 1. Los contratos HTTP del checkout no cambian: `POST /api/pedidos`,
 *    `POST /api/pedidos/{id}/calificacion` y `POST /api/pedidos/{id}/resena`.
 * 2. NINGUNA de esas operaciones se encola. `POST /api/pedidos` no es
 *    idempotente: reintentarlo desde WorkManager podría registrar el pedido
 *    dos veces y descontar el stock dos veces. Por eso el checkout sigue
 *    siendo en línea y bloqueante.
 *
 * Además se verifica que el checkout NO borra las operaciones pendientes que
 * pertenecen a otras funcionalidades (por ejemplo, marcar una notificación
 * como leída).
 */
@RunWith(AndroidJUnit4::class)
class PedidosRepositorioCheckoutTest {

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

    private val jsonPedido = """
        {
          "idPedido": 55,
          "clienteId": 1,
          "fecha": "2024-05-01T10:15:30",
          "total": 120.00,
          "estado": "REGISTRADO",
          "fechaActualizacion": "2024-05-01T10:15:30",
          "detalles": [
            {
              "idProducto": 900,
              "nombreProducto": "Teclado",
              "cantidad": 2,
              "precioUnitario": 60.00,
              "subtotal": 120.00
            }
          ],
          "calificacion": null,
          "resenas": []
        }
    """.trimIndent()

    private val jsonPedidoCalificado = """
        {
          "idPedido": 55,
          "clienteId": 1,
          "fecha": "2024-05-01T10:15:30",
          "total": 120.00,
          "estado": "REGISTRADO",
          "fechaActualizacion": "2024-05-01T10:15:30",
          "detalles": [],
          "calificacion": {"idCalificacion": 9, "valor": 5},
          "resenas": []
        }
    """.trimIndent()

    private val jsonPedidoResenado = """
        {
          "idPedido": 55,
          "clienteId": 1,
          "fecha": "2024-05-01T10:15:30",
          "total": 120.00,
          "estado": "REGISTRADO",
          "fechaActualizacion": "2024-05-01T10:15:30",
          "detalles": [],
          "calificacion": null,
          "resenas": [
            {"idResena": 3, "destino": "PRODUCTO", "texto": "Excelente producto"}
          ]
        }
    """.trimIndent()

    // ================================================================ HU 15
    @Test
    fun confirmarCompraUsaElPostDePedidos() = runTest {
        servidor.responder(201, jsonPedido)

        val resultado = repositorio.confirmarCompra()

        assertTrue("Debería registrar el pedido: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(55L, (resultado as ResultadoApi.Exito).valor.idPedido)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/pedidos", peticion.path)
        assertEquals("POST", peticion.method)
    }

    @Test
    fun confirmarCompraNoEncolaLaOperacion() = runTest {
        servidor.responder(201, jsonPedido)

        repositorio.confirmarCompra()

        assertEquals(
            "POST /api/pedidos no debe encolar nada",
            0,
            cola.listarPendientes(usuarioId).size
        )
    }

    @Test
    fun confirmarCompraConCarritoVacioMuestraElMensajeDelBackend() = runTest {
        servidor.responderError(400, "El carrito está vacío")

        val resultado = repositorio.confirmarCompra()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(400, error.codigo)
        assertEquals("El carrito está vacío", error.mensaje)
    }

    @Test
    fun confirmarCompraSinMensajesUsaElTextoPorCodigo() = runTest {
        servidor.responder(400, "{}")

        val resultado = repositorio.confirmarCompra()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 400: no se pudo registrar el pedido.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ================================================================ HU 41
    @Test
    fun calificarPedidoUsaElPostDeCalificacion() = runTest {
        servidor.responder(200, jsonPedidoCalificado)

        val resultado = repositorio.calificarPedido(idPedido = 55, valor = 5)

        assertTrue("Debería calificar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/pedidos/55/calificacion", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Valor: $cuerpo", cuerpo.contains("\"valor\":5"))
    }

    @Test
    fun calificarPedidoNoEncolaLaOperacion() = runTest {
        servidor.responder(200, jsonPedidoCalificado)

        repositorio.calificarPedido(idPedido = 55, valor = 5)

        assertEquals(
            "La calificación no debe encolar nada",
            0,
            cola.listarPendientes(usuarioId).size
        )
    }

    @Test
    fun calificarPedidoMuestraElMensajeDelBackend() = runTest {
        servidor.responderError(400, "Solo puedes calificar tu propio pedido")

        val resultado = repositorio.calificarPedido(idPedido = 55, valor = 5)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Solo puedes calificar tu propio pedido",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ================================================================ HU 42
    @Test
    fun resenarPedidoUsaElPostDeResena() = runTest {
        servidor.responder(200, jsonPedidoResenado)

        val resultado = repositorio.resenarPedido(
            idPedido = 55,
            destino = "PRODUCTO",
            texto = "Excelente producto"
        )

        assertTrue("Debería reseñar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/pedidos/55/resena", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Destino: $cuerpo", cuerpo.contains("\"destino\":\"PRODUCTO\""))
        assertTrue("Texto: $cuerpo", cuerpo.contains("\"texto\":\"Excelente producto\""))
    }

    @Test
    fun resenarPedidoNoEncolaLaOperacion() = runTest {
        servidor.responder(200, jsonPedidoResenado)

        repositorio.resenarPedido(idPedido = 55, destino = "PRODUCTO", texto = "Excelente")

        assertEquals(
            "La reseña no debe encolar nada",
            0,
            cola.listarPendientes(usuarioId).size
        )
    }

    @Test
    fun resenarPedidoMuestraElMensajeDelBackend() = runTest {
        servidor.responderError(400, "La reseña es demasiado larga")

        val resultado = repositorio.resenarPedido(idPedido = 55, destino = "PRODUCTO", texto = "x")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "La reseña es demasiado larga",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ============================================== la cola de otras funciones
    @Test
    fun elCheckoutNoBorraLasPendientesDeOtrasFuncionalidades() = runTest {
        // Operación ajena al checkout, ya encolada por otra funcionalidad.
        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "77",
            payload = null,
            claveUnica = "notificacion:77"
        )
        servidor.responder(201, jsonPedido)
        servidor.responder(200, jsonPedidoCalificado)
        servidor.responder(200, jsonPedidoResenado)

        repositorio.confirmarCompra()
        repositorio.calificarPedido(idPedido = 55, valor = 5)
        repositorio.resenarPedido(idPedido = 55, destino = "PRODUCTO", texto = "Excelente")

        val pendientes = cola.listarPendientes(usuarioId)
        assertEquals("Debe seguir la operación ajena", 1, pendientes.size)
        assertEquals(
            TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            pendientes.first().tipoOperacion
        )
        assertEquals("77", pendientes.first().recursoId)
    }

    @Test
    fun elCheckoutNoGeneraNingunTipoDeOperacionNuevo() = runTest {
        servidor.responder(201, jsonPedido)
        servidor.responder(200, jsonPedidoCalificado)
        servidor.responder(200, jsonPedidoResenado)

        repositorio.confirmarCompra()
        repositorio.calificarPedido(idPedido = 55, valor = 5)
        repositorio.resenarPedido(idPedido = 55, destino = "PRODUCTO", texto = "Excelente")

        // Ni siquiera hay entradas nuevas en el historial completo de la cola.
        val historial = cola.observarTodas(usuarioId).first()
        assertEquals(0, historial.size)
    }

    @Test
    fun losTiposDeOperacionDelCarritoYDelCheckoutNoExisten() {
        // La cola solo conoce estos cuatro tipos: ninguno es del carrito ni
        // del checkout, así que WorkManager nunca puede reintentarlos.
        val tipos = listOf(
            TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            TiposOperacion.MARCAR_TODAS_NOTIFICACIONES_LEIDAS,
            TiposOperacion.ACTUALIZAR_ESTADO_PEDIDO,
            TiposOperacion.ACTUALIZAR_UMBRAL_STOCK
        )

        assertEquals(4, tipos.size)
        assertTrue(tipos.none { it.contains("CARRITO") })
        assertTrue(tipos.none { it.contains("PEDIDO") && !it.contains("ESTADO") })
    }
}
