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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.ejemplo.publicarproducto.testing.esperarCondicion
import androidx.lifecycle.ViewModel
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo B - DetalleProductoViewModel (HU 03, HU 07, HU 10, HU 31, HU 43 y
 * HU 44).
 *
 * Comprueba la carga del detalle, la disponibilidad, el resumen de
 * calificaciones, las reseñas y los favoritos.
 */
@RunWith(AndroidJUnit4::class)
class DetalleProductoViewModelTest {

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
         "stock":1,"categoria":"Perifericos","estado":"DISPONIBLE",
         "imagenUrl":"/imagenes/7.png","vendedorId":5,"esDestacado":false}
    """.trimIndent()

    private fun encolarDetalleCompleto() {
        servidor.responder(200, cuerpoProducto)
        servidor.responder(
            200,
            """{"idProducto":7,"stock":1,"estado":"DISPONIBLE","disponible":true}"""
        )
        servidor.responder(200, """{"vendedorId":5,"promedio":4.5,"cantidad":2}""")
        servidor.responder(200, "[]")
    }

    @Test
    fun cargarProductoMuestraElDetalleYLaDisponibilidad() {
        encolarDetalleCompleto()
        val model = registrar(DetalleProductoViewModel())
        model.cargarProducto(7)
        esperar { model.uiState is DetalleProductoUiState.Success }
        // El resumen del vendedor y las reseñas se piden en paralelo DESPUÉS
        // de marcar el estado como correcto: se espera a que el servidor los
        // haya recibido para no leer las rutas a medio camino.
        esperar { servidor.peticionesRecibidas() >= 4 }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is DetalleProductoUiState.Success)
        assertEquals("Teclado", model.producto?.nombre)
        assertEquals(1, model.disponibilidad?.stock)
        assertEquals("DISPONIBLE", model.disponibilidad?.estado)

        val rutas = servidor.rutasRecibidas()
        assertTrue("Debe pedir el producto: $rutas", rutas.any { it.contains("/api/productos/7") })
        assertTrue(
            "Debe pedir la disponibilidad: $rutas",
            rutas.any { it.contains("/api/productos/7/disponibilidad") }
        )
        assertTrue(
            "Debe pedir el resumen del vendedor: $rutas",
            rutas.any { it.contains("/api/vendedores/5/calificaciones/resumen") }
        )
        assertTrue(
            "Debe pedir las reseñas: $rutas",
            rutas.any { it.contains("/api/productos/7/resenas") }
        )
    }

    @Test
    fun productoNoEncontradoMuestraElMensajeOriginal() {
        servidor.responder(404, "{}")
        servidor.responder(404, "{}")
        val model = registrar(DetalleProductoViewModel())
        model.cargarProducto(7)
        esperar { model.uiState is DetalleProductoUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is DetalleProductoUiState.Error)
        assertEquals(
            "Producto no encontrado (id 7).",
            (estado as DetalleProductoUiState.Error).mensaje
        )
    }

    @Test
    fun sinConexionMuestraElMensajeDeRed() {
        servidor.apagar()
        val model = registrar(DetalleProductoViewModel())
        model.cargarProducto(7)
        esperar { model.uiState is DetalleProductoUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is DetalleProductoUiState.Error)
        assertTrue(
            "Mensaje de red: ${(estado as DetalleProductoUiState.Error).mensaje}",
            (estado as DetalleProductoUiState.Error).mensaje
                .startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun laCantidadNoSuperaElStockDisponible() {
        encolarDetalleCompleto()
        val model = registrar(DetalleProductoViewModel())
        model.cargarProducto(7)
        esperar { model.uiState is DetalleProductoUiState.Success }

        model.cambiarCantidad(1)
        assertEquals("Con stock 1 no se puede subir", 1, model.cantidad)

        model.cambiarCantidad(-1)
        assertEquals("La cantidad nunca baja de 1", 1, model.cantidad)
    }

    @Test
    fun consultarElEstadoDeFavoritoMarcaElProducto() {
        servidor.responder(200, """{"productoId":7,"favorito":true}""")
        val model = registrar(DetalleProductoViewModel())
        model.cargarEstadoFavorito(7)
        esperar { !model.cargandoFavorito }

        assertTrue("Debe estar en favoritos", model.esFavorito)
        assertEquals(
            "/api/usuarios/me/favoritos/7/estado",
            servidor.ultimaRuta()
        )
    }

    @Test
    fun agregarFavoritoMuestraElAvisoOriginal() {
        encolarDetalleCompleto()
        servidor.responder(200, """{"productoId":7,"favorito":true}""")
        val model = registrar(DetalleProductoViewModel())
        model.cargarProducto(7)
        esperar { model.uiState is DetalleProductoUiState.Success }

        model.agregarFavorito()
        esperar { model.avisoFavorito != null }

        assertEquals("Producto guardado en tus favoritos.", model.avisoFavorito)
        assertTrue("Debe estar en favoritos", model.esFavorito)
    }

    @Test
    fun errorAlConsultarElFavoritoMuestraElMensajeOriginal() {
        servidor.responder(500, "{}")
        val model = registrar(DetalleProductoViewModel())
        model.cargarEstadoFavorito(7)
        esperar { model.avisoFavorito != null }

        assertEquals(
            "Error 500: no se pudo consultar el estado del favorito.",
            model.avisoFavorito
        )
    }
}
