package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.ProductoResponse
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
 * Grupo B - BuscarProductosViewModel (HU 06, HU 07, HU 09, HU 20, HU 31 y
 * HU 33).
 *
 * Comprueba la búsqueda, el catálogo, las recomendaciones y los favoritos
 * contra el repositorio, con los mensajes de error de antes.
 */
@RunWith(AndroidJUnit4::class)
class BuscarProductosViewModelTest {

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

    @Test
    fun unTerminoVacioNoLlamaAlBackend() {
        val model = registrar(BuscarProductosViewModel())
        model.cambiarNombre("   ")
        model.buscarProductos()

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is BuscarProductosUiState.Error)
        assertEquals(
            "Ingresa un nombre de producto para buscar.",
            (estado as BuscarProductosUiState.Error).mensaje
        )
        assertEquals(0, servidor.peticionesRecibidas())
    }

    @Test
    fun buscarMuestraLosResultadosYPideLasRecomendaciones() {
        // La búsqueda y las recomendaciones se piden en paralelo.
        servidor.responder(200, "[$cuerpoProducto]")
        servidor.responder(200, "[$cuerpoProducto]")
        val model = registrar(BuscarProductosViewModel())
        // El criterio de orden se fija antes de escribir el nombre: cambiar
        // el orden con un nombre ya escrito dispararía una búsqueda extra.
        model.cambiarOrden("precio")
        model.cambiarNombre("teclado")
        model.buscarProductos()
        esperar {
            model.uiState is BuscarProductosUiState.Success &&
                model.uiStateRecomendaciones is RecomendacionesUiState.Success
        }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is BuscarProductosUiState.Success)
        assertEquals(1, model.productos.size)

        val rutas = servidor.rutasRecibidas()
        assertEquals(2, rutas.size)
        assertTrue("Debe buscar: $rutas", rutas.any { it.contains("/api/productos?nombre=teclado") })
        assertTrue("Debe ordenar: $rutas", rutas.any { it.contains("sort=precio") })
        assertTrue("Debe recomendar: $rutas", rutas.any { it.contains("/api/recomendaciones?termino=teclado") })
    }

    @Test
    fun errorEnLaBusquedaMuestraElMensajeOriginal() {
        servidor.responder(500, "{}")
        servidor.responder(500, "{}")
        val model = registrar(BuscarProductosViewModel())
        model.cambiarNombre("teclado")
        model.buscarProductos()
        esperar { model.uiState is BuscarProductosUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is BuscarProductosUiState.Error)
        assertEquals(
            "Error 500: no se pudo realizar la búsqueda.",
            (estado as BuscarProductosUiState.Error).mensaje
        )
        assertTrue("La lista queda vacía", model.productos.isEmpty())
    }

    @Test
    fun cargarCatalogoMuestraElCatalogoPublico() {
        servidor.responder(200, "[$cuerpoProducto]")
        val model = registrar(BuscarProductosViewModel())
        model.cargarCatalogo()
        esperar { model.uiState is BuscarProductosUiState.Success }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is BuscarProductosUiState.Success)
        assertEquals(1, model.productos.size)
        assertEquals("/api/productos/catalogo", servidor.ultimaRuta())
    }

    @Test
    fun lasRecomendacionesVisiblesDescartanLasQueYaEstanEnLosResultados() {
        servidor.responder(200, "[$cuerpoProducto]")
        servidor.responder(200, "[$cuerpoProducto]")
        val model = registrar(BuscarProductosViewModel())
        model.cambiarNombre("teclado")
        model.buscarProductos()
        esperar {
            model.uiState is BuscarProductosUiState.Success &&
                model.uiStateRecomendaciones is RecomendacionesUiState.Success
        }

        // El mismo producto está en los resultados y en las recomendaciones.
        assertEquals(1, model.productos.size)
        assertEquals(1, model.recomendaciones.size)
        assertTrue(
            "La recomendación repetida no se muestra: ${model.recomendacionesVisibles}",
            model.recomendacionesVisibles.isEmpty()
        )
    }

    @Test
    fun cargarFavoritosLlenaLaListaDelCliente() {
        servidor.responder(200, "[$cuerpoProducto]")
        val model = registrar(BuscarProductosViewModel())
        model.cargarFavoritos()
        esperar { model.favoritos.isNotEmpty() }

        assertEquals(1, model.favoritos.size)
        assertEquals("/api/usuarios/me/favoritos", servidor.ultimaRuta())
    }

    @Test
    fun agregarFavoritoMuestraElAvisoYActualizaLaLista() {
        val model = registrar(BuscarProductosViewModel())
        val producto = ProductoResponse(
            id = 7,
            nombre = "Teclado",
            descripcion = null,
            precio = null,
            stock = null,
            categoria = null,
            estado = null,
            imagenUrl = null,
            vendedorId = 5
        )
        servidor.responder(200, """{"productoId":7,"favorito":true}""")

        model.agregarFavorito(producto)
        esperar { model.avisoFavorito != null }

        assertEquals("Teclado se guardó en tus favoritos.", model.avisoFavorito)
        assertEquals(1, model.favoritos.size)
        assertEquals(
            "/api/usuarios/me/favoritos/7",
            servidor.ultimaRuta()
        )
    }

    @Test
    fun quitarUnFavoritoInexistenteAvisaQueLaListaSeActualizo() {
        val model = registrar(BuscarProductosViewModel())
        val producto = ProductoResponse(
            id = 7,
            nombre = "Teclado",
            descripcion = null,
            precio = null,
            stock = null,
            categoria = null,
            estado = null,
            imagenUrl = null,
            vendedorId = 5
        )
        servidor.responder(404, "{}")

        model.quitarFavorito(producto)
        esperar { model.avisoFavorito != null }

        assertEquals(
            "El favorito ya no existía; la lista fue actualizada.",
            model.avisoFavorito
        )
    }
}
