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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.ejemplo.publicarproducto.testing.esperarCondicion
import androidx.lifecycle.ViewModel
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo B - PlanPremiumViewModel (HU 18).
 *
 * Comprueba que la consulta del plan va por el repositorio, que los textos de
 * error se conservan y que la selección sigue siendo informativa (sin pagos).
 */
@RunWith(AndroidJUnit4::class)
class PlanPremiumViewModelTest {

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

    @Test
    fun cargarPlanMuestraElPlanRecibido() {
        servidor.responder(
            200,
            """{"nombre":"Premium","descripcion":"Plan destacado","beneficios":["Destacado"]}"""
        )
        val model = registrar(PlanPremiumViewModel())
        model.cargarPlan()
        esperar { model.uiState is PlanPremiumUiState.Success }

        assertTrue("Estado actual: ${model.uiState}", model.uiState is PlanPremiumUiState.Success)
        assertEquals("Premium", model.plan?.nombre)
        assertEquals("/api/planes/premium", servidor.ultimaRuta())
    }

    @Test
    fun errorDelBackendMuestraElMensajeOriginal() {
        servidor.responder(500, "{}")
        val model = registrar(PlanPremiumViewModel())
        model.cargarPlan()
        esperar { model.uiState is PlanPremiumUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is PlanPremiumUiState.Error)
        assertEquals(
            "No se pudo consultar el plan Premium. Error HTTP 500.",
            (estado as PlanPremiumUiState.Error).mensaje
        )
        assertNull("El plan queda vacío", model.plan)
    }

    @Test
    fun sinConexionMuestraElMensajeDeRed() {
        servidor.apagar()
        val model = registrar(PlanPremiumViewModel())
        model.cargarPlan()
        esperar { model.uiState is PlanPremiumUiState.Error }

        val estado = model.uiState
        assertTrue("Estado actual: $estado", estado is PlanPremiumUiState.Error)
        assertEquals(
            "No hay conexión con el backend. Verifica que Spring Boot esté encendido.",
            (estado as PlanPremiumUiState.Error).mensaje
        )
    }

    @Test
    fun seleccionarPlanSoloFuncionaConElPlanCargado() {
        val model = registrar(PlanPremiumViewModel())
        model.seleccionarPlan()
        assertFalse("Sin plan no hay selección", model.seleccionado)

        servidor.responder(
            200,
            """{"nombre":"Premium","descripcion":"Plan destacado","beneficios":["Destacado"]}"""
        )
        model.cargarPlan()
        esperar { model.uiState is PlanPremiumUiState.Success }

        model.seleccionarPlan()
        assertTrue("Con plan hay selección", model.seleccionado)
    }
}
