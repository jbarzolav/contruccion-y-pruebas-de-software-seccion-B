package com.ejemplo.publicarproducto.viewmodel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ejemplo.publicarproducto.model.conversaciones.ConversacionResumenResponse
import com.ejemplo.publicarproducto.model.conversaciones.MensajeConsulta
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.view.navigation.CoordinadorSondeoAvisos
import com.ejemplo.publicarproducto.view.navigation.ObservadorPrimerPlano
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R1 - El sondeo de HU39 no debe correr con la aplicación en segundo plano.
 *
 * La auditoría independiente detectó que el sondeo del aviso de mensaje nuevo
 * (`AvisosConversacionViewModel`) seguía activo con la app oculta: es un
 * ViewModel de alcance `Activity` y su trabajo se creaba en `viewModelScope`,
 * sin ligadura con `Lifecycle.State.STARTED`.
 *
 * Esta prueba verifica el contrato completo **con el cableado real**:
 *
 * ```
 * LifecycleRegistry → ObservadorPrimerPlano → CoordinadorSondeoAvisos
 *                  → AvisosConversacionViewModel → peticiones HTTP
 * ```
 *
 * No se llama a `pausar()` a mano en ningún momento: la parada tiene que
 * llegar por el callback del ciclo de vida, que es el único mecanismo que
 * AndroidX garantiza cuando la aplicación pasa a segundo plano (Compose no
 * garantiza recomponer después de `ON_STOP`).
 *
 * El ciclo de vida se simula con un `LifecycleRegistry` real (el de
 * `androidx.lifecycle`), de modo que la prueba ejercita el mismo código que
 * usa la navegación.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SondeoPrimerPlanoTest {

    private lateinit var owner: LifecycleOwner
    private lateinit var registro: LifecycleRegistry
    private lateinit var observador: ObservadorPrimerPlano

    /** Peticiones "HTTP" contabilizadas por el sondeo. */
    private var peticiones = 0

    private var viewModel: AvisosConversacionViewModel? = null

    /** `true` si la pantalla de Consultas está realmente en pantalla. */
    private var consultasVisible = false

    /** Identidad del usuario con sesión (`null` si la sesión se cerró). */
    private var usuarioActivo: Long? = 42L

    /** Contenido que devuelve el "servidor" de la prueba. */
    private var contenido: List<ConversacionResumenResponse> = emptyList()

    /** Ejecuta la acción en el hilo principal (el ciclo de vida lo exige). */
    private fun enPrincipal(accion: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(accion)
    }

    /**
     * Monta el cableado real de la navegación: ciclo de vida → observador →
     * coordinador → ViewModel de avisos.
     */
    private fun montarCableadoReal() {
        val model = AvisosConversacionViewModel {
            peticiones++
            ResultadoApi.Exito(contenido)
        }
        viewModel = model

        val coordinador = CoordinadorSondeoAvisos(
            usuarioActivoAhora = { usuarioActivo },
            consultasActivasAhora = { consultasVisible },
            alIniciarSondeo = { usuarioId -> model.iniciar(usuarioId) },
            alPausarSondeo = { model.pausar() }
        )

        enPrincipal {
            owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle = LifecycleRegistry(this)
            }
            registro = owner.lifecycle as LifecycleRegistry
            observador = ObservadorPrimerPlano(registro) { enPrimerPlano ->
                coordinador.onCambioPrimerPlano(enPrimerPlano)
            }
            observador.iniciar()
        }
    }

    @After
    fun tearDown() {
        viewModel?.detener()
        viewModel = null
        Dispatchers.resetMain()
    }

    @Test
    fun elObservadorReflejaElPrimerPlanoDeLaAplicacion() {
        val cambios = mutableListOf<Boolean>()
        enPrincipal {
            owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle = LifecycleRegistry(this)
            }
            registro = owner.lifecycle as LifecycleRegistry
            observador = ObservadorPrimerPlano(registro) { valor -> cambios.add(valor) }
            observador.iniciar()
        }

        // Un `LifecycleRegistry` recién creado está en `INITIALIZED`: todavía
        // no se ha dispatched `ON_START`, luego el indicador debe ser `false`.
        assertFalse(
            "Antes de ON_START la aplicación no está en primer plano",
            observador.enPrimerPlano
        )

        // `ON_START` / `ON_RESUME`: la aplicación pasa a primer plano.
        enPrincipal { registro.currentState = Lifecycle.State.RESUMED }
        assertTrue(
            "Al crear la pantalla, la app ya está en primer plano",
            observador.enPrimerPlano
        )

        // `ON_STOP`: la aplicación pasa a segundo plano.
        enPrincipal { registro.currentState = Lifecycle.State.CREATED }
        assertFalse(
            "R1 - Al pasar a segundo plano debe dejar de estar en primer plano",
            observador.enPrimerPlano
        )

        // La aplicación vuelve al primer plano.
        enPrincipal { registro.currentState = Lifecycle.State.RESUMED }
        assertTrue(
            "Al volver al primer plano debe volver a estarlo",
            observador.enPrimerPlano
        )

        // El cambio se notifica de forma síncrona, sin esperar a ninguna
        // recomposición.
        assertEquals(
            "El observador debe notificar cada cambio de primer plano",
            listOf(false, true, false, true),
            cambios.toList()
        )

        // Tras `detener()` el observador ya no reacciona a los eventos.
        enPrincipal { observador.detener() }
        enPrincipal { registro.currentState = Lifecycle.State.CREATED }
        assertTrue(
            "Tras detener el observador debe conservar el último estado",
            observador.enPrimerPlano
        )
        assertEquals(
            "Tras detener el observador no debe notificar más cambios",
            4,
            cambios.size
        )
    }

    @Test
    fun noHayPeticionesHttpConLaAplicacionEnSegundoPlano() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        montarCableadoReal()
        val model = viewModel!!

        try {
            // 1) En primer plano y fuera de Consultas: el sondeo corre.
            enPrincipal { registro.currentState = Lifecycle.State.RESUMED }
            runCurrent()
            assertTrue("En primer plano debe haber peticiones", peticiones > 0)

            // 2) La aplicación pasa a segundo plano: el ciclo de vida cancela
            //    el sondeo por su cuenta (no se llama a `pausar()` a mano).
            enPrincipal { registro.currentState = Lifecycle.State.CREATED }
            runCurrent()

            val peticionesAlPasarASegundoPlano = peticiones
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(
                "R1 - Con la aplicación en segundo plano no debe haber peticiones HTTP",
                peticionesAlPasarASegundoPlano,
                peticiones
            )

            // 3) La aplicación vuelve al primer plano: el sondeo se reanuda.
            enPrincipal { registro.currentState = Lifecycle.State.RESUMED }
            runCurrent()
            advanceTimeBy(30_000)
            runCurrent()

            assertTrue(
                "Al volver al primer plano el sondeo debe reanudarse",
                peticiones > peticionesAlPasarASegundoPlano
            )
        } finally {
            // El sondeo es un bucle infinito de 15 s: hay que detenerlo ANTES
            // de que `runTest` intente dejar el planificador en reposo (si no,
            // la prueba no terminaría nunca).
            model.detener()
            runCurrent()
        }
    }

    @Test
    fun conLaPantallaDeConsultasVisibleNoSondeaElViewModelDeAvisos() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        consultasVisible = true
        montarCableadoReal()
        val model = viewModel!!

        try {
            enPrincipal { registro.currentState = Lifecycle.State.RESUMED }
            runCurrent()

            val peticionesIniciales = peticiones
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(
                "R3 - Con la pantalla de Consultas visible, el ViewModel de avisos no debe sondear",
                peticionesIniciales,
                peticiones
            )
        } finally {
            model.detener()
            runCurrent()
        }
    }

    @Test
    fun laRecreacionDeLaActivityConservaLaMemoriaDeMensajesVistos() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        contenido = listOf(resumen(10L, "Producto inicial"))
        montarCableadoReal()
        val model = viewModel!!

        try {
            enPrincipal { registro.currentState = Lifecycle.State.RESUMED }
            runCurrent()
            assertNull("La carga inicial no avisa por los mensajes anteriores", model.avisoMensaje)

            // Llega un mensaje nuevo.
            contenido = listOf(resumen(11L, "Producto nuevo"))
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals("Producto nuevo", model.avisoMensaje)

            // R1/M2 - Recreación de la Activity: el ciclo de vida pausa el
            // sondeo y, al volver, la memoria de mensajes vistos se conserva.
            enPrincipal { registro.currentState = Lifecycle.State.CREATED }
            runCurrent()

            contenido = listOf(resumen(12L, "Producto posterior"))
            enPrincipal { registro.currentState = Lifecycle.State.RESUMED }
            runCurrent()

            assertEquals(
                "No se deben perder los mensajes que llegan durante la recreación",
                "Producto posterior",
                model.avisoMensaje
            )
        } finally {
            model.detener()
            runCurrent()
        }
    }

    private fun resumen(idMensaje: Long, producto: String, emisorId: Long = 7L) =
        ConversacionResumenResponse(
            idConversacion = 1,
            productoId = 2,
            productoNombre = producto,
            productoImagenUrl = null,
            fechaCreacion = "2025-01-01T00:00:00",
            ultimoMensaje = MensajeConsulta(
                id = idMensaje,
                emisorId = emisorId,
                emisorRol = "CLIENTE",
                contenido = "Mensaje",
                fecha = "2025-01-01T00:00:00"
            )
        )
}
