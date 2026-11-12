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
import java.math.BigDecimal
import com.ejemplo.publicarproducto.testing.esperarCondicion
import androidx.lifecycle.ViewModel
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo C - ConfirmarCompraViewModel (HU 15, HU 41 y HU 42).
 *
 * Comprueba que el resumen y el checkout ya no hablan con Retrofit: el
 * carrito y el total vienen de `CarritoRepositorio` y el pedido, la
 * calificación y la reseña de `PedidosRepositorio`.
 *
 * También verifica que el checkout NO genera entradas en la cola de
 * operaciones: `POST /api/pedidos` no es idempotente.
 */
@RunWith(AndroidJUnit4::class)
class ConfirmarCompraViewModelTest {

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

    private val jsonPedido = """
        {"idPedido":55,"clienteId":1,"fecha":"2024-05-01T10:15:30","total":300.00,
         "estado":"REGISTRADO","fechaActualizacion":"2024-05-01T10:15:30",
         "detalles":[],"calificacion":null,"resenas":[]}
    """.trimIndent()

    private val jsonPedidoCalificado = """
        {"idPedido":55,"clienteId":1,"fecha":"2024-05-01T10:15:30","total":300.00,
         "estado":"REGISTRADO","fechaActualizacion":"2024-05-01T10:15:30",
         "detalles":[],"calificacion":{"idCalificacion":9,"valor":5},"resenas":[]}
    """.trimIndent()

    private val jsonPedidoResenado = """
        {"idPedido":55,"clienteId":1,"fecha":"2024-05-01T10:15:30","total":300.00,
         "estado":"REGISTRADO","fechaActualizacion":"2024-05-01T10:15:30",
         "detalles":[],"calificacion":null,
         "resenas":[{"idResena":3,"destino":"PRODUCTO","texto":"Excelente producto"}]}
    """.trimIndent()

    /** Encola el resumen (carrito + total). */
    private fun encolarResumen() {
        servidor.responder(200, "[$cuerpoItem]")
        servidor.responder(200, """{"total":300.00}""")
    }

    // ------------------------------------------------------------------ HU 15
    @Test
    fun cargarResumenMuestraLosItemsYElTotal() {
        encolarResumen()
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar {
            model.uiState is ConfirmarCompraUiState.Success &&
                    model.totalCarrito > BigDecimal.ZERO
        }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is ConfirmarCompraUiState.Success)
        assertEquals(1, model.items.size)
        assertEquals("300.00", model.totalCarrito.toPlainString())
    }

    @Test
    fun errorAlCargarElResumenMuestraElMensajeOriginal() {
        servidor.responder(500, "{}")
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is ConfirmarCompraUiState.Error)
        assertEquals(
            "Error 500: no se pudo cargar el resumen.",
            (estado as ConfirmarCompraUiState.Error).mensaje
        )
    }

    @Test
    fun sinConexionAlCargarElResumenMuestraElMensajeDeRed() {
        servidor.apagar()
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is ConfirmarCompraUiState.Error)
        assertTrue(
            "Mensaje de red: ${(estado as ConfirmarCompraUiState.Error).mensaje}",
            (estado as ConfirmarCompraUiState.Error).mensaje
                .startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun confirmarCompraRegistraElPedidoYVaciaElCarrito() {
        encolarResumen()
        servidor.responder(201, jsonPedido)
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Success }

        model.confirmarCompra()
        esperar { model.pedido != null }

        assertEquals(55L, model.pedido?.idPedido)
        assertTrue("El carrito debe quedar vacío", model.items.isEmpty())
        assertEquals(0, model.totalCarrito.compareTo(BigDecimal.ZERO))
        assertNull(model.mensajeError)
        val rutas = servidor.rutasRecibidas()
        assertTrue("Las rutas: $rutas", rutas.contains("/api/pedidos"))
    }

    @Test
    fun conElCarritoVacioNoSeLlamaAlBackend() {
        servidor.responder(200, "[]")
        servidor.responder(200, """{"total":0.00}""")
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Success }

        val peticionesAntes = servidor.peticionesRecibidas()
        model.confirmarCompra()

        assertEquals("No debe llamar al backend", peticionesAntes, servidor.peticionesRecibidas())
        assertNull(model.pedido)
    }

    @Test
    fun errorAlConfirmarMuestraElMensajeDelBackend() {
        encolarResumen()
        servidor.responderError(400, "El carrito está vacío")
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Success }

        model.confirmarCompra()
        esperar { model.mensajeError != null }

        assertEquals("El carrito está vacío", model.mensajeError)
        assertNull(model.pedido)
    }

    // ------------------------------------------------------------------ HU 41
    @Test
    fun calificarGuardaLaCalificacionDelPedido() {
        encolarResumen()
        servidor.responder(201, jsonPedido)
        servidor.responder(200, jsonPedidoCalificado)
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Success }
        model.confirmarCompra()
        esperar { model.pedido != null }

        model.calificar(5)
        esperar { model.pedido?.calificacion != null }

        assertNotNull(model.pedido?.calificacion)
        assertEquals(5, model.pedido?.calificacion?.valor)
        assertNull(model.mensajeExperiencia)
        val rutas = servidor.rutasRecibidas()
        assertTrue("Las rutas: $rutas", rutas.contains("/api/pedidos/55/calificacion"))
    }

    @Test
    fun sinPedidoNoSePuedeCalificar() {
        val model = registrar(ConfirmarCompraViewModel())
        model.calificar(5)

        assertEquals(0, servidor.peticionesRecibidas())
        assertNull(model.mensajeExperiencia)
    }

    // ------------------------------------------------------------------ HU 42
    @Test
    fun resenarPublicaLaResenaDelPedido() {
        encolarResumen()
        servidor.responder(201, jsonPedido)
        servidor.responder(200, jsonPedidoResenado)
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Success }
        model.confirmarCompra()
        esperar { model.pedido != null }

        model.textoResena = "  Excelente producto  "
        model.resenar()
        esperar { model.pedido?.resenas?.isNotEmpty() == true }

        assertEquals(1, model.pedido?.resenas?.size)
        assertEquals("Excelente producto", model.pedido?.resenas?.first()?.texto)
        assertEquals("", model.textoResena)
        val rutas = servidor.rutasRecibidas()
        assertTrue("Las rutas: $rutas", rutas.contains("/api/pedidos/55/resena"))
    }

    @Test
    fun unaResenaVaciaNoSeEnvia() {
        encolarResumen()
        servidor.responder(201, jsonPedido)
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Success }
        model.confirmarCompra()
        esperar { model.pedido != null }

        val peticionesAntes = servidor.peticionesRecibidas()
        model.textoResena = "   "
        model.resenar()

        assertEquals("No debe llamar al backend", peticionesAntes, servidor.peticionesRecibidas())
    }

    // ================================================== la cola no se toca
    @Test
    fun elCheckoutNoEncolaNingunaOperacion() {
        encolarResumen()
        servidor.responder(201, jsonPedido)
        servidor.responder(200, jsonPedidoCalificado)
        servidor.responder(200, jsonPedidoResenado)
        val model = registrar(ConfirmarCompraViewModel())
        model.cargarResumen()
        esperar { model.uiState is ConfirmarCompraUiState.Success }
        model.confirmarCompra()
        esperar { model.pedido != null }
        model.calificar(5)
        esperar { model.pedido?.calificacion != null }
        // La reseña solo se envía si hay texto: sin él el ViewModel descarta
        // la llamada y el pedido seguiría sin resenas.
        model.textoResena = "Excelente producto"
        model.resenar()
        esperar { model.pedido?.resenas?.isNotEmpty() == true }

        val cola = ProveedorDatos.actual.cola
        val pendientes = kotlinx.coroutines.runBlocking { cola.listarPendientes(1L) }
        assertEquals("El checkout no debe encolar nada", 0, pendientes.size)
    }
}
