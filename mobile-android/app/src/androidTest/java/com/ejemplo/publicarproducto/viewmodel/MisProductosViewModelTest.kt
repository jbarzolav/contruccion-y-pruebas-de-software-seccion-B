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
 * Grupo B - MisProductosViewModel (HU 05 y HU 19).
 *
 * Comprueba que el ViewModel delega en ProductosRepositorio, que los textos
 * de error son los de antes y que el destacado recarga la lista.
 */
@RunWith(AndroidJUnit4::class)
class MisProductosViewModelTest {

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

    /** Espera en tiempo real a que el ViewModel termine (corre en el hilo principal). */
    private fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
    }

    private val cuerpoProducto = """
        {"id":7,"nombre":"Teclado","descripcion":"Teclado mecanico","precio":150.0,
         "stock":5,"categoria":"Perifericos","estado":"DISPONIBLE",
         "imagenUrl":"/imagenes/7.png","vendedorId":5,"esDestacado":true}
    """.trimIndent()

    @Test
    fun cargarProductosMuestraLaListaDelVendedor() {
        servidor.responder(200, "[$cuerpoProducto]")
        val model = registrar(MisProductosViewModel())
        model.cargarProductos()
        esperar { model.uiState is MisProductosUiState.Success }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is MisProductosUiState.Success)
        assertEquals(1, model.productos.size)
        assertEquals("Teclado", model.productos.first().nombre)
        assertEquals(
            "/api/vendedores/me/productos?vendedorId=5",
            servidor.ultimaRuta()
        )
    }

    @Test
    fun errorDelBackendMuestraElMensajeOriginal() {
        servidor.responder(500, "{}")
        val model = registrar(MisProductosViewModel())
        model.cargarProductos()
        esperar { model.uiState is MisProductosUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is MisProductosUiState.Error)
        assertEquals(
            "Error 500: no se pudieron cargar los productos.",
            (estado as MisProductosUiState.Error).mensaje
        )
    }

    @Test
    fun sinConexionMuestraElMensajeDeRed() {
        servidor.apagar()
        val model = registrar(MisProductosViewModel())
        model.cargarProductos()
        esperar { model.uiState is MisProductosUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is MisProductosUiState.Error)
        assertTrue(
            "Mensaje de red: ${(estado as MisProductosUiState.Error).mensaje}",
            (estado as MisProductosUiState.Error).mensaje
                .startsWith("No hay conexión con el backend (10.0.2.2:9000):")
        )
    }

    @Test
    fun gestionarDestacadoActualizaYRecargaLaLista() {
        servidor.responder(200, cuerpoProducto)
        servidor.responder(200, "[$cuerpoProducto]")
        val model = registrar(MisProductosViewModel())
        model.gestionarDestacado(7, destacado = true)
        esperar { model.uiState is MisProductosUiState.Success }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is MisProductosUiState.Success)
        val rutas = servidor.rutasRecibidas()
        assertEquals(2, rutas.size)
        assertTrue("Debe destacar: $rutas", rutas[0].contains("/api/productos/7/destacado"))
        assertTrue("Debe recargar: $rutas", rutas[1].contains("/api/vendedores/me/productos"))
    }

    @Test
    fun errorAlDestacarMuestraElMensajeOriginal() {
        servidor.responder(500, "{}")
        val model = registrar(MisProductosViewModel())
        model.gestionarDestacado(7, destacado = true)
        esperar { model.uiState is MisProductosUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is MisProductosUiState.Error)
        assertEquals(
            "Error 500: no se pudo actualizar el destacado.",
            (estado as MisProductosUiState.Error).mensaje
        )
    }
}
