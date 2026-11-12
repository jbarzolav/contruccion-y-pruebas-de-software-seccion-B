package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebasUnitario
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo C - CarritoRepositorio (HU 11, HU 12, HU 13 y HU 14).
 *
 * Pruebas unitarias (JVM, sin emulador) contra un servidor HTTP real de
 * pruebas: verifican los textos de error, el uso del primer mensaje de
 * `messages[]` y que los contratos HTTP del carrito no cambian.
 */
class CarritoRepositorioTest {

    private lateinit var servidor: ServidorDePruebasUnitario
    private lateinit var repositorio: CarritoRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebasUnitario()
        repositorio = CarritoRepositorio(servidor.api)
    }

    @After
    fun tearDown() {
        servidor.cerrar()
    }

    private val cuerpoItem = """
        {"id":1,"productoId":7,"nombreProducto":"Teclado","cantidad":2,
         "precioUnitario":150.00,"subtotal":300.00,"stockDisponible":5}
    """.trimIndent()

    // ------------------------------------------------------------------ HU 11
    @Test
    fun agregarItemUsaElPostDelCarrito() = runBlocking {
        servidor.responder(200, cuerpoItem)

        val resultado = repositorio.agregarItem(idProducto = 7, cantidad = 2)

        assertTrue("Debería agregar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/carrito/items", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar el producto: $cuerpo", cuerpo.contains("\"productoId\":7"))
        assertTrue("Debe enviar la cantidad: $cuerpo", cuerpo.contains("\"cantidad\":2"))
    }

    @Test
    fun agregarItemMuestraElPrimerMensajeDelBackend() = runBlocking {
        servidor.responderError(400, "No hay stock suficiente")

        val resultado = repositorio.agregarItem(idProducto = 7, cantidad = 2)

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(400, error.codigo)
        assertEquals("No hay stock suficiente", error.mensaje)
    }

    @Test
    fun agregarItemSinMensajesUsaElTextoPorCodigo() = runBlocking {
        servidor.responder(400, "{}")

        val resultado = repositorio.agregarItem(idProducto = 7, cantidad = 2)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 400: no se pudo agregar.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun agregarItemSinConexionConservaElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.agregarItem(idProducto = 7, cantidad = 2)

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue(error.temporal)
        assertTrue(
            "Mensaje de red: ${error.mensaje}",
            error.mensaje.startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    // ------------------------------------------------------------------ HU 12
    @Test
    fun obtenerCarritoDevuelveLosItems() = runBlocking {
        servidor.responder(200, "[$cuerpoItem]")

        val resultado = repositorio.obtenerCarrito()

        assertTrue("Debería listar: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(1, (resultado as ResultadoApi.Exito).valor.size)
        assertEquals("/api/carrito/items", servidor.ultimaRuta())
    }

    @Test
    fun unCarritoVacioNoEsUnError() = runBlocking {
        servidor.responder(200, "[]")

        val resultado = repositorio.obtenerCarrito()

        assertTrue(resultado is ResultadoApi.Exito)
        assertTrue((resultado as ResultadoApi.Exito).valor.isEmpty())
    }

    @Test
    fun obtenerCarritoConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.obtenerCarrito()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 500: no se pudo cargar el carrito.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun obtenerCarritoSinConexionConservaElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.obtenerCarrito()

        assertTrue(resultado is ResultadoApi.Error)
        assertTrue(
            (resultado as ResultadoApi.Error).mensaje
                .startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun actualizarCantidadUsaElPutDelItem() = runBlocking {
        servidor.responder(200, cuerpoItem)

        val resultado = repositorio.actualizarCantidad(idItem = 1, cantidad = 3)

        assertTrue("Debería actualizar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/carrito/items/1", peticion.path)
        assertEquals("PUT", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar la cantidad: $cuerpo", cuerpo.contains("\"cantidad\":3"))
    }

    @Test
    fun actualizarCantidadMuestraElPrimerMensajeDelBackend() = runBlocking {
        servidor.responderError(400, "La cantidad supera el stock disponible")

        val resultado = repositorio.actualizarCantidad(idItem = 1, cantidad = 99)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "La cantidad supera el stock disponible",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun actualizarCantidadSinMensajesUsaElTextoPorCodigo() = runBlocking {
        servidor.responder(409, "{}")

        val resultado = repositorio.actualizarCantidad(idItem = 1, cantidad = 3)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 409: no se pudo actualizar.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 13
    @Test
    fun eliminarItemUsaElDeleteYDevuelveExitoSinCuerpo() = runBlocking {
        servidor.responder(204, "")

        val resultado = repositorio.eliminarItem(idItem = 1)

        assertTrue("Debería eliminar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/carrito/items/1", peticion.path)
        assertEquals("DELETE", peticion.method)
    }

    @Test
    fun eliminarItemMuestraElPrimerMensajeDelBackend() = runBlocking {
        servidor.responderError(404, "El ítem ya no está en el carrito")

        val resultado = repositorio.eliminarItem(idItem = 1)

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(404, error.codigo)
        assertEquals("El ítem ya no está en el carrito", error.mensaje)
    }

    @Test
    fun eliminarItemSinMensajesUsaElTextoPorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.eliminarItem(idItem = 1)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 500: no se pudo actualizar.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun eliminarItemSinConexionConservaElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.eliminarItem(idItem = 1)

        assertTrue(resultado is ResultadoApi.Error)
        assertTrue(
            (resultado as ResultadoApi.Error).mensaje
                .startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    // ------------------------------------------------------------------ HU 14
    @Test
    fun obtenerTotalUsaLaRutaDelTotal() = runBlocking {
        servidor.responder(200, """{"total":300.00}""")

        val resultado = repositorio.obtenerTotal()

        assertTrue("Debería consultar el total: $resultado", resultado is ResultadoApi.Exito)
        assertEquals("/api/carrito/total", servidor.ultimaRuta())
    }

    @Test
    fun obtenerTotalConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.obtenerTotal()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 500: no se pudo consultar el total.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun obtenerTotalSinConexionConservaElMensajePropio() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.obtenerTotal()

        assertTrue(resultado is ResultadoApi.Error)
        assertTrue(
            "Mensaje de total: ${(resultado as ResultadoApi.Error).mensaje}",
            (resultado as ResultadoApi.Error).mensaje
                .startsWith("No se pudo consultar el total:")
        )
    }
}
