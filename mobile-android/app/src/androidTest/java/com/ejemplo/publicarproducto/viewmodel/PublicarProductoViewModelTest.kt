package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import android.app.Application
import android.content.Context
import android.net.Uri
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.ejemplo.publicarproducto.testing.esperarCondicion
import androidx.lifecycle.ViewModel
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo B - PublicarProductoViewModel (HU 01 y HU 02).
 *
 * Comprueba la validación local, el alta del producto y la subida de la
 * imagen en multipart, con los mensajes de error de antes.
 */
@RunWith(AndroidJUnit4::class)
class PublicarProductoViewModelTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas
    private lateinit var imagenUri: Uri

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

        // Imagen real en el caché: se lee igual que una foto de la galería.
        val archivo = File(contexto.cacheDir, "teclado.png")
        archivo.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        imagenUri = Uri.fromFile(archivo)
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

    private fun model() = registrar(PublicarProductoViewModel(contexto as Application))
    private val cuerpoProducto = """
        {"id":7,"nombre":"Teclado","descripcion":"Teclado mecanico","precio":150.0,
         "stock":5,"categoria":"Perifericos","estado":"DISPONIBLE",
         "imagenUrl":"/imagenes/7.png","vendedorId":5,"esDestacado":false}
    """.trimIndent()

    private fun completarFormulario(model: PublicarProductoViewModel) {
        model.onNombreChange("Teclado")
        model.onDescripcionChange("Teclado mecanico")
        model.onPrecioChange("150")
        model.onStockChange("5")
        model.onCategoriaChange("Perifericos")
        model.onImagenChange(imagenUri)
    }

    @Test
    fun laValidacionLocalNoLlamaAlBackend() {
        val model = model()

        model.onNombreChange("Teclado")
        model.publicar()

        assertEquals("La imagen es obligatoria", model.erroresCampo["imagen"])
        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is PublicarUiState.Error)
        assertEquals(
            "Corrige los campos marcados antes de publicar.",
            (estado as PublicarUiState.Error).mensaje
        )
        assertEquals(0, servidor.peticionesRecibidas())
    }

    @Test
    fun unPrecioInvalidoSeReportaSinLlamarAlBackend() {
        val model = model()

        completarFormulario(model)
        model.onPrecioChange("0")
        model.publicar()

        assertEquals("El precio debe ser mayor a 0", model.erroresCampo["precio"])
        assertEquals(0, servidor.peticionesRecibidas())
    }

    @Test
    fun publicarRegistraElProductoYSubeLaImagen() {
        servidor.responder(200, cuerpoProducto)
        servidor.responder(200, cuerpoProducto)
        val model = model()

        completarFormulario(model)
        model.publicar()
        esperar { model.uiState is PublicarUiState.Success }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is PublicarUiState.Success)
        assertEquals(
            "¡Producto e imagen registrados correctamente! (ID 7)",
            (estado as PublicarUiState.Success).mensaje
        )
        // El formulario se limpia tras el éxito.
        assertEquals("", model.nombre)
        assertNull(model.imagenUri)

        assertEquals("Debe hacer dos peticiones", 2, servidor.peticionesRecibidas())
        assertEquals("/api/productos", servidor.ultimaRuta())
        val peticion = servidor.ultimaPeticion()
        assertEquals("/api/productos/7/imagenes", peticion.path)
        assertEquals("POST", peticion.method)
        assertTrue(
            "Debe enviarse como multipart",
            peticion.getHeader("Content-Type")?.contains("multipart/form-data") == true
        )
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar el campo file: $cuerpo", cuerpo.contains("name=\"file\""))
        assertTrue("Debe enviar el vendedor: $cuerpo", cuerpo.contains("name=\"vendedorId\""))
    }

    @Test
    fun errorAlRegistrarElProductoMuestraElMensajeDelBackend() {
        servidor.responderError(400, "El nombre es obligatorio")
        val model = model()

        completarFormulario(model)
        model.publicar()
        esperar { model.uiState is PublicarUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is PublicarUiState.Error)
        assertEquals(
            "El nombre es obligatorio",
            (estado as PublicarUiState.Error).mensaje
        )
        assertEquals("Solo se intenta el alta", 1, servidor.peticionesRecibidas())
    }

    @Test
    fun errorAlSubirLaImagenAvisaQueElProductoYaSeCreo() {
        servidor.responder(200, cuerpoProducto)
        servidor.responder(400, "{}")
        val model = model()

        completarFormulario(model)
        model.publicar()
        esperar { model.uiState is PublicarUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is PublicarUiState.Error)
        assertEquals(
            "Error 400: el producto fue creado, pero no se pudo subir la imagen.",
            (estado as PublicarUiState.Error).mensaje
        )
    }

    @Test
    fun sinIdEnLaRespuestaSeMuestraElMensajeOriginal() {
        servidor.responder(200, """{"nombre":"Teclado"}""")
        val model = model()

        completarFormulario(model)
        model.publicar()
        esperar { model.uiState is PublicarUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is PublicarUiState.Error)
        assertEquals(
            "No se pudo obtener el ID del producto.",
            (estado as PublicarUiState.Error).mensaje
        )
    }
}
