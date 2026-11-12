package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo C - CarritoRepositorio en el dispositivo (HU 11, HU 12, HU 13 y
 * HU 14).
 *
 * Comprueba en el runtime real del emulador que los endpoints del carrito,
 * sus cuerpos y sus mensajes de error no cambian.
 */
class CarritoRepositorioTest {

    private lateinit var servidor: ServidorDePruebas
    private lateinit var repositorio: CarritoRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebas()
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

    @Test
    fun agregarItemUsaElPostDelCarrito() = runBlocking {
        servidor.responder(200, cuerpoItem)

        val resultado = repositorio.agregarItem(idProducto = 7, cantidad = 2)

        assertTrue("Debería agregar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/carrito/items", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Producto: $cuerpo", cuerpo.contains("\"productoId\":7"))
        assertTrue("Cantidad: $cuerpo", cuerpo.contains("\"cantidad\":2"))
    }

    @Test
    fun agregarItemMuestraElPrimerMensajeDelBackend() = runBlocking {
        servidor.responderError(400, "No hay stock suficiente")

        val resultado = repositorio.agregarItem(idProducto = 7, cantidad = 2)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No hay stock suficiente",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun sinConexionElMensajeEsElDeCarrito() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.obtenerCarrito()

        assertTrue(resultado is ResultadoApi.Error)
        assertTrue(
            (resultado as ResultadoApi.Error).mensaje
                .startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun obtenerCarritoUsaLaRutaDelCarrito() = runBlocking {
        servidor.responder(200, "[$cuerpoItem]")

        val resultado = repositorio.obtenerCarrito()

        assertTrue("Debería listar: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(1, (resultado as ResultadoApi.Exito).valor.size)
        assertEquals("/api/carrito/items", servidor.ultimaRuta())
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
        assertTrue("Cantidad: $cuerpo", cuerpo.contains("\"cantidad\":3"))
    }

    @Test
    fun actualizarCantidadMuestraElMensajeDelBackend() = runBlocking {
        servidor.responderError(400, "La cantidad supera el stock disponible")

        val resultado = repositorio.actualizarCantidad(idItem = 1, cantidad = 99)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "La cantidad supera el stock disponible",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun eliminarItemUsaElDelete() = runBlocking {
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
}
