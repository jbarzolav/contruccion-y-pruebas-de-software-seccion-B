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
 * Grupo B - EditarProductoViewModel (HU 03 y HU 04).
 *
 * Comprueba la precarga del formulario, la validación local, el guardado y
 * la baja lógica de la publicación.
 */
@RunWith(AndroidJUnit4::class)
class EditarProductoViewModelTest {

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
        {"id":7,"nombre":"Teclado","descripcion":"Teclado mecanico","precio":150.00,
         "stock":5,"categoria":"Perifericos","estado":"DISPONIBLE",
         "imagenUrl":"/imagenes/7.png","vendedorId":5,"esDestacado":false}
    """.trimIndent()

    @Test
    fun unIdNoNumericoNoLlamaAlBackend() {
        val model = registrar(EditarProductoViewModel())
        model.onIdChange("abc")
        model.cargarProducto()

        assertEquals("El ID debe ser un número", model.erroresCampo["id"])
        assertEquals(0, servidor.peticionesRecibidas())
    }

    @Test
    fun cargarProductoPrecargaElFormulario() {
        servidor.responder(200, cuerpoProducto)
        val model = registrar(EditarProductoViewModel())
        model.onIdChange("7")
        model.cargarProducto()
        esperar { model.uiState is EditarUiState.Success }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is EditarUiState.Success)
        assertEquals("Producto 7 cargado correctamente.", (estado as EditarUiState.Success).mensaje)
        assertEquals("Teclado", model.nombre)
        assertEquals("Teclado mecanico", model.descripcion)
        assertEquals("150.00", model.precio)
        assertEquals("5", model.stock)
        assertEquals("Perifericos", model.categoria)
        assertEquals("DISPONIBLE", model.estado)
        assertEquals("/api/productos/7", servidor.ultimaRuta())
    }

    @Test
    fun productoNoEncontradoMuestraElMensajeOriginal() {
        servidor.responder(404, "{}")
        val model = registrar(EditarProductoViewModel())
        model.onIdChange("7")
        model.cargarProducto()
        esperar { model.uiState is EditarUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is EditarUiState.Error)
        assertEquals(
            "El producto no existe (HTTP 404).",
            (estado as EditarUiState.Error).mensaje
        )
    }

    @Test
    fun guardarSinProductoCargadoMuestraElAvisoOriginal() {
        val model = registrar(EditarProductoViewModel())
        model.guardar()

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is EditarUiState.Error)
        assertEquals(
            "Primero carga un producto con su ID.",
            (estado as EditarUiState.Error).mensaje
        )
        assertEquals(0, servidor.peticionesRecibidas())
    }

    @Test
    fun unPrecioInvalidoNoLlamaAlBackend() {
        servidor.responder(200, cuerpoProducto)
        val model = registrar(EditarProductoViewModel())
        model.onIdChange("7")
        model.cargarProducto()
        esperar { model.uiState is EditarUiState.Success }

        model.onPrecioChange("0")
        model.guardar()

        assertEquals("El precio debe ser mayor a 0", model.erroresCampo["precio"])
        // Solo la carga inicial: el guardado no se envía.
        assertEquals(1, servidor.peticionesRecibidas())
    }

    @Test
    fun guardarEnviaElPutYConfirmaElCambio() {
        servidor.responder(200, cuerpoProducto)
        servidor.responder(200, cuerpoProducto)
        val model = registrar(EditarProductoViewModel())
        model.onIdChange("7")
        model.cargarProducto()
        esperar { model.uiState is EditarUiState.Success }

        model.onNombreChange("Teclado Pro")
        model.guardar()
        esperar { model.uiState is EditarUiState.Success }

        assertEquals("Debe hacer dos peticiones", 2, servidor.peticionesRecibidas())
        // La primera petición fue la carga; la segunda, el guardado.
        assertEquals("/api/productos/7", servidor.ultimaRuta())
        val peticion = servidor.ultimaPeticion()
        assertEquals("PUT", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar el nombre nuevo: $cuerpo", cuerpo.contains("Teclado Pro"))
    }

    @Test
    fun errorAlGuardarMuestraElMensajeDelBackend() {
        servidor.responder(200, cuerpoProducto)
        servidor.responderError(400, "El precio debe ser mayor a 0")
        val model = registrar(EditarProductoViewModel())
        model.onIdChange("7")
        model.cargarProducto()
        esperar { model.uiState is EditarUiState.Success }

        model.guardar()
        esperar { model.uiState is EditarUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is EditarUiState.Error)
        assertEquals(
            "El precio debe ser mayor a 0",
            (estado as EditarUiState.Error).mensaje
        )
    }

    @Test
    fun retirarPideConfirmacionYAplicaLaBajaLogica() {
        servidor.responder(200, cuerpoProducto)
        servidor.responder(
            200,
            """
            {"id":7,"nombre":"Teclado","descripcion":"Teclado mecanico","precio":150.00,
             "stock":5,"categoria":"Perifericos","estado":"RETIRADO",
             "imagenUrl":"/imagenes/7.png","vendedorId":5,"esDestacado":false}
            """.trimIndent()
        )
        val model = registrar(EditarProductoViewModel())
        model.onIdChange("7")
        model.cargarProducto()
        esperar { model.uiState is EditarUiState.Success }

        model.pedirConfirmacionRetiro()
        assertTrue("Debe pedir confirmación", model.mostrarDialogoRetiro)

        model.retirar()
        esperar { model.uiState is EditarUiState.Success }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is EditarUiState.Success)
        assertEquals(
            "Publicación retirada (estado RETIRADO). No se eliminó: es una baja lógica.",
            (estado as EditarUiState.Success).mensaje
        )
        assertTrue("El producto queda retirado", model.estaRetirado)
        // La primera petición fue la carga; la segunda, la baja lógica.
        assertEquals("/api/productos/7", servidor.ultimaRuta())
        val peticion = servidor.ultimaPeticion()
        assertEquals("PATCH", peticion.method)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar RETIRADO: $cuerpo", cuerpo.contains("\"estado\":\"RETIRADO\""))
    }
}
