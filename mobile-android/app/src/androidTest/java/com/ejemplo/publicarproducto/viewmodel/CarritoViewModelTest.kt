package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
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
import java.math.BigDecimal
import com.ejemplo.publicarproducto.testing.esperarCondicion
import androidx.lifecycle.ViewModel
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo C - CarritoViewModel (HU 12, HU 13 y HU 14).
 *
 * Comprueba que el ViewModel del carrito ya no habla con Retrofit: delega en
 * `CarritoRepositorio`, conserva los mensajes que mostraba la interfaz y
 * mantiene las reglas de cantidad y de actualización del subtotal.
 */
@RunWith(AndroidJUnit4::class)
class CarritoViewModelTest {

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

    private val cuerpoItem = """
        {"id":1,"productoId":7,"nombreProducto":"Teclado","cantidad":2,
         "precioUnitario":150.00,"subtotal":300.00,"stockDisponible":5}
    """.trimIndent()

    private fun item(id: Long = 1L, cantidad: Int = 2): ItemCarritoResponse =
        ItemCarritoResponse(
            id = id,
            productoId = 7,
            nombreProducto = "Teclado",
            cantidad = cantidad,
            precioUnitario = BigDecimal("150.00"),
            subtotal = BigDecimal("300.00"),
            stockDisponible = 5
        )

    // ------------------------------------------------------------------ HU 12
    @Test
    fun cargarCarritoMuestraLosItemsYElTotalDelBackend() {
        servidor.responder(200, "[$cuerpoItem]")
        servidor.responder(200, """{"total":300.00}""")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Success && model.totalCarrito > BigDecimal.ZERO }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is CarritoUiState.Success)
        assertEquals(1, model.items.size)
        assertEquals("Teclado", model.items.first().nombreProducto)
        assertEquals("300.00", model.totalCarrito.toPlainString())

        val rutas = servidor.rutasRecibidas()
        assertTrue("Debe pedir el carrito: $rutas", rutas.contains("/api/carrito/items"))
        assertTrue("Debe pedir el total: $rutas", rutas.contains("/api/carrito/total"))
    }

    @Test
    fun unCarritoVacioSeMuestraSinError() {
        servidor.responder(200, "[]")
        servidor.responder(200, """{"total":0.00}""")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Success }

        assertTrue(model.uiState is CarritoUiState.Success)
        assertTrue(model.items.isEmpty())
    }

    @Test
    fun errorAlCargarElCarritoMuestraElMensajeOriginal() {
        servidor.responder(500, "{}")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is CarritoUiState.Error)
        assertEquals(
            "Error 500: no se pudo cargar el carrito.",
            (estado as CarritoUiState.Error).mensaje
        )
    }

    @Test
    fun sinConexionMuestraElMensajeDeRed() {
        servidor.apagar()
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is CarritoUiState.Error)
        assertTrue(
            "Mensaje de red: ${(estado as CarritoUiState.Error).mensaje}",
            (estado as CarritoUiState.Error).mensaje
                .startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun cambiarCantidadLlamaAlPutYActualizaElItem() {
        servidor.responder(200, "[$cuerpoItem]")
        servidor.responder(200, """{"total":300.00}""")
        servidor.responder(
            200,
            """{"id":1,"productoId":7,"nombreProducto":"Teclado","cantidad":3,
                "precioUnitario":150.00,"subtotal":450.00,"stockDisponible":5}"""
        )
        servidor.responder(200, """{"total":450.00}""")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Success && model.totalCarrito > BigDecimal.ZERO }

        model.cambiarCantidad(model.items.first(), 1)
        esperar {
            model.items.firstOrNull()?.cantidad == 3 &&
                    model.totalCarrito.toPlainString() == "450.00"
        }

        assertEquals(3, model.items.first().cantidad)
        assertEquals("450.00", model.totalCarrito.toPlainString())
        val rutas = servidor.rutasRecibidas()
        assertTrue("Debe usar el PUT del ítem: $rutas", rutas.contains("/api/carrito/items/1"))
    }

    @Test
    fun laCantidadNuncaBajaDeUno() {
        servidor.responder(200, "[$cuerpoItem]")
        servidor.responder(200, """{"total":300.00}""")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Success }

        val peticionesAntes = servidor.peticionesRecibidas()
        model.cambiarCantidad(item(cantidad = 1), -1)

        assertEquals("No debe llamar al backend", peticionesAntes, servidor.peticionesRecibidas())
        assertNull(model.mensajeError)
    }

    @Test
    fun errorAlActualizarLaCantidadMuestraElMensajeDelBackend() {
        servidor.responder(200, "[$cuerpoItem]")
        servidor.responder(200, """{"total":300.00}""")
        servidor.responderError(400, "La cantidad supera el stock disponible")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Success }

        model.cambiarCantidad(model.items.first(), 1)
        esperar { model.mensajeError != null }

        assertEquals("La cantidad supera el stock disponible", model.mensajeError)
        assertEquals("No debe cambiar el ítem", 2, model.items.first().cantidad)
    }

    // ------------------------------------------------------------------ HU 13
    @Test
    fun eliminarProductoQuitaElItemYRecargaElTotal() {
        servidor.responder(200, "[$cuerpoItem]")
        servidor.responder(200, """{"total":300.00}""")
        servidor.responder(204, "")
        servidor.responder(200, """{"total":0.00}""")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Success && model.totalCarrito > BigDecimal.ZERO }

        model.eliminarProducto(model.items.first())
        esperar { model.items.isEmpty() && model.totalCarrito.toPlainString() == "0.00" }

        assertTrue(model.items.isEmpty())
        val rutas = servidor.rutasRecibidas()
        assertTrue("Debe usar el DELETE del ítem: $rutas", rutas.contains("/api/carrito/items/1"))
    }

    @Test
    fun errorAlEliminarMuestraElMensajeDelBackend() {
        servidor.responder(200, "[$cuerpoItem]")
        servidor.responder(200, """{"total":300.00}""")
        servidor.responderError(404, "El ítem ya no está en el carrito")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.uiState is CarritoUiState.Success }

        model.eliminarProducto(model.items.first())
        esperar { model.mensajeError != null }

        assertEquals("El ítem ya no está en el carrito", model.mensajeError)
        assertEquals("No debe quitar el ítem", 1, model.items.size)
    }

    // ------------------------------------------------------------------ HU 14
    @Test
    fun errorAlConsultarElTotalMuestraElMensajeOriginal() {
        servidor.responder(200, "[$cuerpoItem]")
        servidor.responder(500, "{}")
        val model = registrar(CarritoViewModel())
        model.cargarCarrito()
        esperar { model.mensajeError != null }

        assertEquals("Error 500: no se pudo consultar el total.", model.mensajeError)
        assertNotNull(model.items)
    }
}
