package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.testing.ContenedorDePruebas
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.ejemplo.publicarproducto.testing.esperarCondicion
import androidx.lifecycle.ViewModel
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo C - DetalleProductoViewModel y el carrito (HU 11).
 *
 * Verifica que `agregarAlCarrito` ya no hace ninguna llamada directa a
 * Retrofit: delega en `CarritoRepositorio` y conserva los mensajes que
 * mostraba la interfaz (éxito con el subtotal del backend y el primer
 * mensaje de error del backend).
 */
@RunWith(AndroidJUnit4::class)
class DetalleProductoCarritoTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas

    /**
     * ViewModels creados por la prueba.
     *
     * Sus colectores de Room viven en `viewModelScope`: hay que cancelar ese
     * alcance ANTES de cerrar la base (lección D-3), o Room consultaría una
     * base ya cerrada y el proceso de pruebas se caería.
     */
    private val viewModels = mutableListOf<ViewModel>()

    /** Crea un ViewModel registrado para cancelar su alcance al terminar. */
    private fun <T : ViewModel> registrar(model: T): T {
        viewModels += model
        return model
    }

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesion = SessionManager(contexto)
        sesion.invalidarSesion()
        servidor = ServidorDePruebas()
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))
    }

    @After
    fun tearDown() {
        // Primero se detienen los colectores de Room y después se cierra la
        // base: al revés, Room consultaría una base ya cerrada.
        viewModels.forEach { it.cancelarAlcance() }
        viewModels.clear()

        ProveedorDatos.fijar(null)
        sesion.invalidarSesion()

        bd.close()
        servidor.cerrar()
    }

    private fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
    }

    private val cuerpoProducto = """
        {"id":7,"nombre":"Teclado","descripcion":"Teclado mecanico","precio":150.0,
         "stock":5,"categoria":"Perifericos","estado":"DISPONIBLE",
         "imagenUrl":"/imagenes/7.png","vendedorId":5,"esDestacado":false}
    """.trimIndent()

    private val cuerpoItem = """
        {"id":1,"productoId":7,"nombreProducto":"Teclado","cantidad":2,
         "precioUnitario":150.00,"subtotal":300.00,"stockDisponible":5}
    """.trimIndent()

    /** Encola el detalle completo del producto (4 peticiones). */
    private fun encolarDetalleCompleto() {
        servidor.responder(200, cuerpoProducto)
        servidor.responder(
            200,
            """{"idProducto":7,"stock":5,"estado":"DISPONIBLE","disponible":true}"""
        )
        servidor.responder(200, """{"vendedorId":5,"promedio":4.5,"cantidad":2}""")
        servidor.responder(200, "[]")
    }

    private fun cargarProducto(model: DetalleProductoViewModel) {
        encolarDetalleCompleto()
        model.cargarProducto(7)
        esperar { model.uiState is DetalleProductoUiState.Success }
        // El resumen del vendedor y las reseñas se piden en paralelo: se
        // espera a que el servidor los haya recibido para que la petición
        // del carrito sea siempre la última y no se mezcle el orden.
        esperar { servidor.peticionesRecibidas() >= 4 }
    }

    @Test
    fun agregarAlCarritoMuestraElMensajeDeExito() {
        val model = registrar(DetalleProductoViewModel())
        cargarProducto(model)
        servidor.responder(200, cuerpoItem)

        model.cambiarCantidad(1)
        model.agregarAlCarrito()
        esperar { model.mensajeCarrito != null }

        assertEquals(
            "✓ Se agregó 2 unidad(es) al carrito (subtotal S/ 300.00)",
            model.mensajeCarrito
        )
        assertNull(model.errorCarrito)
    }

    @Test
    fun agregarAlCarritoUsaElPostDelCarritoConLaCantidadElegida() {
        val model = registrar(DetalleProductoViewModel())
        cargarProducto(model)
        servidor.responder(200, cuerpoItem)

        model.cambiarCantidad(1)
        model.agregarAlCarrito()
        esperar { model.mensajeCarrito != null }

        val peticion = servidor.peticionCarrito()
        assertNotNull("Debe enviar la petición del carrito", peticion)
        assertEquals("/api/carrito/items", peticion!!.path)
        assertEquals("POST", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Producto: $cuerpo", cuerpo.contains("\"productoId\":7"))
        assertTrue("Cantidad: $cuerpo", cuerpo.contains("\"cantidad\":2"))
    }

    @Test
    fun errorAlAgregarAlCarritoMuestraElMensajeDelBackend() {
        val model = registrar(DetalleProductoViewModel())
        cargarProducto(model)
        servidor.responderError(400, "No hay stock suficiente")

        model.agregarAlCarrito()
        esperar { model.errorCarrito != null }

        assertEquals("No hay stock suficiente", model.errorCarrito)
        assertNull(model.mensajeCarrito)
    }

    @Test
    fun errorSinMensajesMuestraElTextoPorCodigo() {
        val model = registrar(DetalleProductoViewModel())
        cargarProducto(model)
        servidor.responder(400, "{}")

        model.agregarAlCarrito()
        esperar { model.errorCarrito != null }

        assertEquals("Error 400: no se pudo agregar.", model.errorCarrito)
    }

    @Test
    fun sinConexionMuestraElMensajeDeRed() {
        val model = registrar(DetalleProductoViewModel())
        cargarProducto(model)
        servidor.apagar()

        model.agregarAlCarrito()
        esperar { model.errorCarrito != null }

        assertNotNull(model.errorCarrito)
        assertTrue(
            "Mensaje de red: ${model.errorCarrito}",
            model.errorCarrito!!.startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun sinProductoNoSeLlamaAlBackend() {
        val model = registrar(DetalleProductoViewModel())
        model.agregarAlCarrito()

        assertEquals(0, servidor.peticionesRecibidas())
        assertNull(model.mensajeCarrito)
        assertNull(model.errorCarrito)
    }
}
