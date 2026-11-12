package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.EstadoProductoRequest
import com.ejemplo.publicarproducto.model.ProductoRequest
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
 * Grupo B - ProductosRepositorio en el dispositivo (HU 01 a HU 19).
 *
 * Complementa las pruebas unitarias de `src/test`: aquí el repositorio se
 * ejecuta sobre el runtime real del emulador y se comprueban los contratos
 * HTTP (ruta, método, query y multipart) contra el servidor de pruebas.
 */
class ProductosRepositorioTest {

    private lateinit var servidor: ServidorDePruebas
    private lateinit var repositorio: ProductosRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebas()
        repositorio = ProductosRepositorio(servidor.api) { 42L }
    }

    @After
    fun tearDown() {
        servidor.cerrar()
    }

    private val cuerpoProducto = """
        {"id":7,"nombre":"Teclado","descripcion":"Teclado mecanico","precio":150.0,
         "stock":5,"categoria":"Perifericos","estado":"DISPONIBLE",
         "imagenUrl":"/imagenes/7.png","vendedorId":42,"esDestacado":false}
    """.trimIndent()

    private fun request() = ProductoRequest(
        nombre = "Teclado",
        descripcion = "Teclado mecanico",
        precio = 150.0,
        stock = 5,
        categoria = "Perifericos",
        estado = "DISPONIBLE",
        imagenUrl = "",
        vendedorId = 0L
    )

    @Test
    fun listarMisProductosUsaElVendedorDeLaSesion() = runBlocking {
        servidor.responder(200, "[$cuerpoProducto]")

        val resultado = repositorio.listarMisProductos()

        assertTrue("Debería listar: $resultado", resultado is ResultadoApi.Exito)
        assertEquals(1, (resultado as ResultadoApi.Exito).valor.size)
        assertEquals("/api/vendedores/me/productos?vendedorId=42", servidor.ultimaRuta())
    }

    @Test
    fun listarMisProductosConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.listarMisProductos()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertEquals(500, error.codigo)
        assertEquals("Error 500: no se pudieron cargar los productos.", error.mensaje)
    }

    @Test
    fun sinConexionElErrorEsTemporalYConservaElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.listarMisProductos()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue(error.temporal)
        assertTrue(
            "Mensaje de red: ${error.mensaje}",
            error.mensaje.startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun buscarEnviaElNombreYElOrden() = runBlocking {
        servidor.responder(200, "[$cuerpoProducto]")

        repositorio.buscar(nombre = "teclado", sort = "precio")

        assertEquals("/api/productos?nombre=teclado&sort=precio", servidor.ultimaRuta())
    }

    @Test
    fun listarCatalogoYRecomendacionesConservanSusRutas() = runBlocking {
        servidor.responder(200, "[]")
        repositorio.listarCatalogo()
        assertEquals("/api/productos/catalogo", servidor.ultimaRuta())

        servidor.responder(200, "[]")
        repositorio.listarRecomendaciones(termino = "teclado")
        assertEquals("/api/recomendaciones?termino=teclado", servidor.ultimaRuta())
    }

    @Test
    fun obtenerProductoYDetalleUsanTextosPropios() = runBlocking {
        servidor.responder(404, "{}")
        val edicion = repositorio.obtenerProducto(7)
        assertEquals(
            "El producto no existe (HTTP 404).",
            (edicion as ResultadoApi.Error).mensaje
        )

        servidor.responder(404, "{}")
        val detalle = repositorio.obtenerDetalleProducto(7)
        assertEquals(
            "Producto no encontrado (id 7).",
            (detalle as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun crearProductoCompletaElVendedorYLaImagenPendiente() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.crearProducto(request())

        assertTrue("Debería crear: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Vendedor de la sesión: $cuerpo", cuerpo.contains("\"vendedorId\":42"))
        assertTrue("Imagen pendiente: $cuerpo", cuerpo.contains("https://via.placeholder.com/300"))
    }

    @Test
    fun subirImagenEnviaElMultipartConLosCamposOriginales() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.subirImagen(
            idProducto = 7,
            bytes = "contenido-de-imagen".toByteArray(),
            nombreArchivo = "teclado.png",
            mime = "image/png"
        )

        assertTrue("Debería subir: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos/7/imagenes", peticion.path)
        assertEquals("POST", peticion.method)
        assertTrue(
            "Debe enviarse como multipart",
            peticion.getHeader("Content-Type")?.contains("multipart/form-data") == true
        )
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Campo file: $cuerpo", cuerpo.contains("name=\"file\""))
        assertTrue("Nombre real: $cuerpo", cuerpo.contains("teclado.png"))
        assertTrue("Campo vendedorId: $cuerpo", cuerpo.contains("name=\"vendedorId\""))
    }

    @Test
    fun actualizarProductoUsaElPut() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.actualizarProducto(
            idProducto = 7,
            request = request(),
            vendedorIdPropietario = 42L
        )

        assertTrue("Debería actualizar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos/7?vendedorId=42", peticion.path)
        assertEquals("PUT", peticion.method)
    }

    @Test
    fun cambiarEstadoAplicaLaBajaLogica() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.cambiarEstado(
            idProducto = 7,
            request = EstadoProductoRequest("RETIRADO"),
            vendedorIdPropietario = 42L
        )

        assertTrue("Debería retirar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos/7/estado?vendedorId=42", peticion.path)
        assertEquals("PATCH", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Estado RETIRADO: $cuerpo", cuerpo.contains("\"estado\":\"RETIRADO\""))
    }

    @Test
    fun gestionarDestacadoEnviaElIndicador() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.gestionarDestacado(idProducto = 7, destacado = true)

        assertTrue("Debería destacar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos/7/destacado?vendedorId=42", peticion.path)
        assertEquals("PATCH", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Indicador destacado: $cuerpo", cuerpo.contains("\"destacado\":true"))
    }

    @Test
    fun consultarPlanPremiumUsaLaRutaDelPlan() = runBlocking {
        servidor.responder(
            200,
            """{"nombre":"Premium","descripcion":"Plan destacado","beneficios":["Destacado"]}"""
        )

        val resultado = repositorio.consultarPlanPremium()

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals("/api/planes/premium", servidor.ultimaRuta())
    }

    @Test
    fun consultarPlanPremiumConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.consultarPlanPremium()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No se pudo consultar el plan Premium. Error HTTP 500.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }
}
