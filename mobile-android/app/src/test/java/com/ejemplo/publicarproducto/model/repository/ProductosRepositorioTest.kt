package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.EstadoProductoRequest
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebasUnitario
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Grupo B - ProductosRepositorio (HU 01, 02, 03, 04, 05, 06, 07, 09, 10,
 * 18, 19, 20, 43 y 44).
 *
 * Pruebas unitarias (JVM, sin emulador) contra un servidor HTTP real de
 * pruebas: verifican los textos de error, el vendedor tomado de la sesión y
 * que los contratos HTTP (ruta, método, query y multipart) no cambian.
 */
class ProductosRepositorioTest {

    private lateinit var servidor: ServidorDePruebasUnitario
    private lateinit var repositorio: ProductosRepositorio

    @Before
    fun setUp() {
        servidor = ServidorDePruebasUnitario()
        repositorio = ProductosRepositorio(servidor.api) { 42L }
    }

    @After
    fun tearDown() {
        servidor.cerrar()
    }

    private fun request(
        nombre: String = "Teclado",
        descripcion: String = "Teclado mecánico",
        precio: Double = 150.0,
        stock: Int = 5,
        categoria: String = "Periféricos",
        estado: String = "DISPONIBLE",
        imagenUrl: String = "",
        vendedorId: Long = 0L
    ) = ProductoRequest(
        nombre = nombre,
        descripcion = descripcion,
        precio = precio,
        stock = stock,
        categoria = categoria,
        estado = estado,
        imagenUrl = imagenUrl,
        vendedorId = vendedorId
    )

    private val cuerpoProducto = """
        {"id":7,"nombre":"Teclado","descripcion":"Teclado mecánico","precio":150.0,
         "stock":5,"categoria":"Perifericos","estado":"DISPONIBLE",
         "imagenUrl":"/imagenes/7.png","vendedorId":42,"esDestacado":false}
    """.trimIndent()

    // ------------------------------------------------------------------ HU 05
    @Test
    fun listarMisProductosDevuelveLaListaYEnviaElVendedorDeLaSesion() = runBlocking {
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
        assertTrue("Un error 5xx es temporal", error.temporal)
    }

    @Test
    fun listarMisProductosSinConexionDevuelveElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.listarMisProductos()

        assertTrue(resultado is ResultadoApi.Error)
        val error = resultado as ResultadoApi.Error
        assertNull(error.codigo)
        assertTrue("Sin red el error es temporal", error.temporal)
        assertTrue(
            "Mensaje de red: ${error.mensaje}",
            error.mensaje.startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun unaListaVaciaEnElBackendNoEsUnError() = runBlocking {
        servidor.responder(200, "[]")

        val resultado = repositorio.listarMisProductos()

        assertTrue(resultado is ResultadoApi.Exito)
        assertTrue((resultado as ResultadoApi.Exito).valor.isEmpty())
    }

    // -------------------------------------------------------------- HU 06/HU 09
    @Test
    fun buscarEnviaElNombreYElCriterioDeOrden() = runBlocking {
        servidor.responder(200, "[$cuerpoProducto]")

        val resultado = repositorio.buscar(nombre = "teclado", sort = "precio")

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals(
            "/api/productos?nombre=teclado&sort=precio",
            servidor.ultimaRuta()
        )
    }

    @Test
    fun buscarSinCriterioDeOrdenNoEnviaElParametroSort() = runBlocking {
        servidor.responder(200, "[]")

        repositorio.buscar(nombre = "teclado", sort = null)

        assertEquals("/api/productos?nombre=teclado", servidor.ultimaRuta())
    }

    @Test
    fun buscarConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(503, "{}")

        val resultado = repositorio.buscar(nombre = "teclado")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 503: no se pudo realizar la búsqueda.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 07
    @Test
    fun listarCatalogoUsaLaRutaDeCatalogo() = runBlocking {
        servidor.responder(200, "[$cuerpoProducto]")

        val resultado = repositorio.listarCatalogo()

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals("/api/productos/catalogo", servidor.ultimaRuta())
    }

    @Test
    fun listarCatalogoConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(404, "{}")

        val resultado = repositorio.listarCatalogo()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 404: no se pudo cargar el catálogo.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 20
    @Test
    fun listarRecomendacionesUsaElTerminoConsultado() = runBlocking {
        servidor.responder(200, "[$cuerpoProducto]")

        val resultado = repositorio.listarRecomendaciones(termino = "teclado")

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals("/api/recomendaciones?termino=teclado", servidor.ultimaRuta())
    }

    @Test
    fun listarRecomendacionesConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.listarRecomendaciones(termino = "teclado")

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 500: no se pudieron cargar las recomendaciones.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun listarRecomendacionesSinConexionConservaElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.listarRecomendaciones(termino = "teclado")

        assertTrue(resultado is ResultadoApi.Error)
        assertTrue(
            (resultado as ResultadoApi.Error).mensaje
                .startsWith("No se pudieron cargar las recomendaciones (10.0.2.2:9000):")
        )
    }

    // ------------------------------------------------------------------ HU 03
    @Test
    fun obtenerProductoUsaLaRutaDeDetalle() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.obtenerProducto(7)

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals(7L, (resultado as ResultadoApi.Exito).valor.id)
        assertEquals("/api/productos/7", servidor.ultimaRuta())
    }

    @Test
    fun obtenerProducto404ConservaElMensajeDeEdicion() = runBlocking {
        servidor.responder(404, "{}")

        val resultado = repositorio.obtenerProducto(7)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "El producto no existe (HTTP 404).",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun obtenerProducto403ConservaElMensajeDePermiso() = runBlocking {
        servidor.responder(403, "{}")

        val resultado = repositorio.obtenerProducto(7)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No tienes permiso sobre este producto (HTTP 403).",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun obtenerProductoLeeLosMensajesDeValidacionDelBackend() = runBlocking {
        servidor.responderError(400, "El precio debe ser mayor a 0")

        val resultado = repositorio.obtenerProducto(7)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "El precio debe ser mayor a 0",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun obtenerDetalleProductoUsaElMensajePropioDeLaPantalla() = runBlocking {
        servidor.responder(404, "{}")

        val resultado = repositorio.obtenerDetalleProducto(7)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Producto no encontrado (id 7).",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 10
    @Test
    fun obtenerDisponibilidadUsaLaRutaDeDisponibilidad() = runBlocking {
        servidor.responder(
            200,
            """{"idProducto":7,"disponible":true,"stock":5,"estado":"DISPONIBLE"}"""
        )

        val resultado = repositorio.obtenerDisponibilidad(7)

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals("/api/productos/7/disponibilidad", servidor.ultimaRuta())
    }

    // ------------------------------------------------------------------ HU 01
    @Test
    fun crearProductoCompletaElVendedorYLaImagenPendiente() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.crearProducto(request())

        assertTrue("Debería crear: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos", peticion.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar el vendedor de la sesión: $cuerpo", cuerpo.contains("\"vendedorId\":42"))
        assertTrue(
            "Debe enviar la imagen pendiente: $cuerpo",
            cuerpo.contains("https://via.placeholder.com/300")
        )
    }

    @Test
    fun crearProductoConservaElMensajeDeValidacion() = runBlocking {
        servidor.responderError(400, "El nombre es obligatorio")

        val resultado = repositorio.crearProducto(request())

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "El nombre es obligatorio",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun crearProducto403ConservaElMensajeDePermiso() = runBlocking {
        servidor.responder(403, "{}")

        val resultado = repositorio.crearProducto(request())

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No tienes permiso sobre este producto (HTTP 403).",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 02
    @Test
    fun subirImagenUsaElMultipartEnLaRutaEsperada() = runBlocking {
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
        assertTrue("Debe enviar el campo file: $cuerpo", cuerpo.contains("name=\"file\""))
        assertTrue("Debe enviar el nombre real: $cuerpo", cuerpo.contains("teclado.png"))
        assertTrue("Debe enviar el vendedor: $cuerpo", cuerpo.contains("name=\"vendedorId\""))
        assertTrue("Debe enviar el vendedor de la sesión: $cuerpo", cuerpo.contains("42"))
        assertTrue("Debe enviar el contenido: $cuerpo", cuerpo.contains("contenido-de-imagen"))
    }

    @Test
    fun subirImagenSinNombreUsaElNombrePorDefecto() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        repositorio.subirImagen(
            idProducto = 7,
            bytes = "contenido".toByteArray(),
            nombreArchivo = null,
            mime = "image/png"
        )

        val cuerpo = servidor.ultimaPeticion().body.readUtf8()
        assertTrue("Nombre por defecto PNG: $cuerpo", cuerpo.contains("producto.png"))
    }

    @Test
    fun subirImagenConservaElMensajeDelBackend() = runBlocking {
        servidor.responderError(400, "El archivo debe ser una imagen")

        val resultado = repositorio.subirImagen(
            idProducto = 7,
            bytes = "contenido".toByteArray(),
            nombreArchivo = "teclado.png",
            mime = "image/png"
        )

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "El archivo debe ser una imagen",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 03
    @Test
    fun actualizarProductoUsaElPutYElDuenoDeLaPublicacion() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.actualizarProducto(
            idProducto = 7,
            request = request(imagenUrl = "/imagenes/7.png"),
            vendedorIdPropietario = 42L
        )

        assertTrue("Debería actualizar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos/7?vendedorId=42", peticion.path)
        assertEquals("PUT", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe conservar la imagen: $cuerpo", cuerpo.contains("/imagenes/7.png"))
        assertTrue("Debe enviar el vendedor: $cuerpo", cuerpo.contains("\"vendedorId\":42"))
    }

    /**
     * HU 03 - El backend exige `imagenUrl` en el cuerpo del PUT (`@NotBlank`)
     * aunque al editar no modifica ese campo. Si el cuerpo llega vacío, el
     * guardado se rechaza con HTTP 400 "La imagen es obligatoria".
     *
     * El repositorio normaliza el valor igual que en el alta, de modo que
     * editar una publicación cuya imagen no se conoce no falle.
     */
    @Test
    fun actualizarProductoCompletaLaImagenSiNoSeConoce() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.actualizarProducto(
            idProducto = 7,
            request = request(imagenUrl = ""),
            vendedorIdPropietario = 42L
        )

        assertTrue("Debería actualizar: $resultado", resultado is ResultadoApi.Exito)
        val cuerpo = servidor.ultimaPeticion().body.readUtf8()
        assertTrue(
            "La imagen no puede viajar vacía (el backend la exige): $cuerpo",
            cuerpo.contains("\"imagenUrl\":\"https://via.placeholder.com/300\"")
        )
    }

    @Test
    fun actualizarProductoConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.actualizarProducto(
            idProducto = 7,
            request = request(),
            vendedorIdPropietario = 42L
        )

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 500: no se pudo actualizar el producto.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 04
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
        assertTrue(
            "Debe enviar el estado RETIRADO: $cuerpo",
            cuerpo.contains("\"estado\":\"RETIRADO\"")
        )
    }

    @Test
    fun cambiarEstadoConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(404, "{}")

        val resultado = repositorio.cambiarEstado(
            idProducto = 7,
            request = EstadoProductoRequest("RETIRADO"),
            vendedorIdPropietario = 42L
        )

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "El producto no existe (HTTP 404).",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 19
    @Test
    fun gestionarDestacadoEnviaElIndicadorYElVendedor() = runBlocking {
        servidor.responder(200, cuerpoProducto)

        val resultado = repositorio.gestionarDestacado(idProducto = 7, destacado = true)

        assertTrue("Debería destacar: $resultado", resultado is ResultadoApi.Exito)
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos/7/destacado?vendedorId=42", peticion.path)
        assertEquals("PATCH", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue(
            "Debe enviar el destacado: $cuerpo",
            cuerpo.contains("\"destacado\":true")
        )
    }

    @Test
    fun gestionarDestacadoConservaElMensajePorCodigo() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.gestionarDestacado(idProducto = 7, destacado = false)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "Error 500: no se pudo actualizar el destacado.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ HU 18
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

    @Test
    fun consultarPlanPremiumSinConexionConservaElMensajeDeRed() = runBlocking {
        servidor.apagar()

        val resultado = repositorio.consultarPlanPremium()

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No hay conexión con el backend. Verifica que Spring Boot esté encendido.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // -------------------------------------------------------------- HU 43/HU 44
    @Test
    fun resumenCalificacionesUsaLaRutaDelVendedor() = runBlocking {
        servidor.responder(
            200,
            """{"promedio":4.5,"total":12,"cantidad":10}"""
        )

        val resultado = repositorio.resumenCalificacionesVendedor(42)

        assertTrue(resultado is ResultadoApi.Exito)
        assertEquals(
            "/api/vendedores/42/calificaciones/resumen",
            servidor.ultimaRuta()
        )
    }

    @Test
    fun resumenCalificacionesConservaElMensajePropio() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.resumenCalificacionesVendedor(42)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No se pudieron cargar las calificaciones del vendedor.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    @Test
    fun listarResenasProductoUsaLaRutaDeResenas() = runBlocking {
        servidor.responder(200, "[]")

        val resultado = repositorio.listarResenasProducto(7)

        assertTrue(resultado is ResultadoApi.Exito)
        assertTrue((resultado as ResultadoApi.Exito).valor.isEmpty())
        assertEquals("/api/productos/7/resenas", servidor.ultimaRuta())
    }

    @Test
    fun listarResenasProductoConservaElMensajePropio() = runBlocking {
        servidor.responder(500, "{}")

        val resultado = repositorio.listarResenasProducto(7)

        assertTrue(resultado is ResultadoApi.Error)
        assertEquals(
            "No se pudieron cargar las reseñas del producto.",
            (resultado as ResultadoApi.Error).mensaje
        )
    }

    // ------------------------------------------------------------------ sesión
    @Test
    fun sinSesionElErrorConservaElMensajeDeInicioDeSesion() = runBlocking {
        val repositorioSinSesion = ProductosRepositorio(servidor.api) { 0L }
        servidor.responder(200, "[]")

        val resultado = repositorioSinSesion.listarMisProductos()

        assertTrue(resultado is ResultadoApi.Error)
        assertTrue(
            "Debe pedir iniciar sesión: ${(resultado as ResultadoApi.Error).mensaje}",
            resultado.mensaje.contains("Debes iniciar sesión.")
        )
    }

    @Test
    fun unErrorDeServidorNoEsTemporalCuandoEs4xx() = runBlocking {
        servidor.responder(404, "{}")

        val resultado = repositorio.obtenerProducto(7)

        assertTrue(resultado is ResultadoApi.Error)
        assertFalse("Un 404 no se reintenta", (resultado as ResultadoApi.Error).temporal)
    }
}
