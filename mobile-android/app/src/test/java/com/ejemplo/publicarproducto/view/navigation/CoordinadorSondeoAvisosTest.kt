package com.ejemplo.publicarproducto.view.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1/R3/M1/M2 - Aplicación de la regla de arbitraje del sondeo de
 * conversaciones.
 *
 * El coordinador es el punto donde la regla pura
 * [debeSonearAvisosDeConversacion] se convierte en acciones sobre el
 * ViewModel de avisos. Estas pruebas comprueban que:
 *
 * - la decisión se aplica de forma **síncrona** (no depende de que Compose
 *   llegue a recomponer);
 * - la pantalla visible y la identidad del usuario se consultan **en el
 *   momento** de decidir, no se guarda una copia;
 * - solo hay un responsable del sondeo en cada combinación;
 * - si la sesión caduca con la aplicación en segundo plano, el sondeo no se
 *   reanuda con una identidad obsoleta.
 *
 * JUnit crea una instancia nueva de la clase de prueba para cada método, así
 * que el coordinador y los contadores empiezan siempre limpios.
 */
class CoordinadorSondeoAvisosTest {

    /** `true` si la pantalla de Consultas está realmente en pantalla. */
    private var consultasVisible = false

    /** Identidad del usuario con sesión (`null` si la sesión se cerró). */
    private var usuarioActivo: Long? = 42L

    /** Acciones aplicadas sobre el ViewModel de avisos, en orden. */
    private val acciones = mutableListOf<String>()

    private val coordinador = CoordinadorSondeoAvisos(
        usuarioActivoAhora = { usuarioActivo },
        consultasActivasAhora = { consultasVisible },
        alIniciarSondeo = { usuarioId -> acciones.add("iniciar:$usuarioId") },
        alPausarSondeo = { acciones.add("pausar") }
    )

    @Test
    fun sinUsuarioActivoNoHaySondeo() {
        usuarioActivo = null

        coordinador.onCambioPrimerPlano(true)

        assertEquals(
            "Sin sesión autenticada no debe sondear nadie",
            listOf("pausar"),
            acciones.toList()
        )
    }

    @Test
    fun enPrimerPlanoYFueraDeConsultasElBadgeSondea() {
        acciones.clear()

        coordinador.onCambioPrimerPlano(true)

        assertEquals(
            "En primer plano y fuera de Consultas debe sondear el badge",
            listOf("iniciar:42"),
            acciones.toList()
        )
    }

    @Test
    fun conLaPantallaDeConsultasVisibleElBadgeNoSondea() {
        consultasVisible = true
        acciones.clear()

        coordinador.onCambioPrimerPlano(true)

        assertEquals(
            "Con la pantalla de Consultas visible el badge no debe sondear",
            listOf("pausar"),
            acciones.toList()
        )
    }

    @Test
    fun alPasarASegundoPlanoElSondeoSePausaDeFormaSincrona() {
        coordinadorEnPrimerPlanoSondeando()
        acciones.clear()

        // La decisión se aplica DENTRO de la llamada: no hay ninguna
        // recomposición ni ninguna corrutura de por medio. Es lo que
        // garantiza el cierre de R1: Compose no garantiza recomponer
        // después de `ON_STOP`.
        coordinador.onCambioPrimerPlano(false)

        assertEquals(
            "R1 - Al pasar a segundo plano el sondeo debe pausarse en el acto",
            listOf("pausar"),
            acciones.toList()
        )
    }

    @Test
    fun alVolverAlPrimerPlanoElSondeoSeReanuda() {
        coordinador.onCambioPrimerPlano(false)
        acciones.clear()

        coordinador.onCambioPrimerPlano(true)

        assertEquals(
            "Al volver al primer plano el sondeo debe reanudarse",
            listOf("iniciar:42"),
            acciones.toList()
        )
    }

    @Test
    fun laDecisionConsultaElEstadoActualDeLaPantalla() {
        consultasVisible = true
        coordinador.onCambioPrimerPlano(true)
        acciones.clear()

        // R1/M1 - Se abre una subpantalla sobre la pestaña Consultas: la
        // pantalla de Consultas sale de la composición y deja de sondear, así
        // que el badge debe asumir el relevo en lugar de quedarse congelado.
        consultasVisible = false
        coordinador.onCambioPrimerPlano(true)

        assertEquals(
            "Con una subpantalla abierta debe sondear el badge",
            listOf("iniciar:42"),
            acciones.toList()
        )
    }

    @Test
    fun conUnaSubpantallaSobreConsultasElBadgeAsumeElRelevo() {
        consultasVisible = true
        coordinador.onCambioPrimerPlano(true)
        acciones.clear()

        // La pantalla de Consultas deja de estar realmente en pantalla: la
        // navegación vuelve a propagar el contexto y el badge asume el relevo.
        consultasVisible = false
        coordinador.reevaluar()

        assertEquals(
            "Al dejar de estar visible la pantalla de Consultas, el badge asume el sondeo",
            listOf("iniciar:42"),
            acciones.toList()
        )
    }

    @Test
    fun alCambiarDeUsuarioSePropagaLaNuevaIdentidad() {
        coordinadorEnPrimerPlanoSondeando()
        acciones.clear()

        usuarioActivo = 9L
        coordinador.reevaluar()

        assertEquals(
            "El sondeo debe pasar a la identidad del nuevo usuario",
            listOf("iniciar:9"),
            acciones.toList()
        )
    }

    @Test
    fun siLaSesionCaducaNoSeReanudaElSondeoEnSegundoPlano() {
        coordinadorEnPrimerPlanoSondeando()

        // La sesión caduca mientras la aplicación está en segundo plano (por
        // ejemplo, un 401 del sincronizador de WorkManager).
        usuarioActivo = null
        acciones.clear()

        // Al volver al primer plano NO debe reanudarse el sondeo: la
        // identidad se consulta en el momento de decidir.
        coordinador.onCambioPrimerPlano(true)

        assertEquals(
            "Con la sesión cerrada no debe reanudarse el sondeo",
            listOf("pausar"),
            acciones.toList()
        )
    }

    @Test
    fun laDecisionCoincideConLaReglaPuraEnLasCuatroCombinaciones() {
        listOf(true, false).forEach { visible ->
            listOf(true, false).forEach { primerPlano ->
                consultasVisible = visible
                acciones.clear()

                coordinador.reevaluar()
                acciones.clear()
                coordinador.onCambioPrimerPlano(primerPlano)

                val esperado = if (debeSonearAvisosDeConversacion(visible, primerPlano)) {
                    "iniciar:42"
                } else {
                    "pausar"
                }
                assertEquals(
                    "La decisión del coordinador debe coincidir con la regla pura " +
                        "(visible=$visible, primerPlano=$primerPlano)",
                    listOf(esperado),
                    acciones.toList()
                )
            }
        }
    }

    @Test
    fun elEstadoDeLaPantallaSeConsultaEnElMomentoDeDecidir() {
        var consultas = 0
        val local = CoordinadorSondeoAvisos(
            usuarioActivoAhora = { 42L },
            consultasActivasAhora = { consultas++; false },
            alIniciarSondeo = { },
            alPausarSondeo = { }
        )

        local.reevaluar()
        local.onCambioPrimerPlano(true)

        assertEquals(
            "La pantalla debe consultarse en cada decisión, no guardarse una copia",
            2,
            consultas
        )
    }

    @Test
    fun laIdentidadSeConsultaEnElMomentoDeDecidir() {
        var consultas = 0
        val local = CoordinadorSondeoAvisos(
            usuarioActivoAhora = { consultas++; 42L },
            consultasActivasAhora = { false },
            alIniciarSondeo = { },
            alPausarSondeo = { }
        )

        local.reevaluar()
        local.onCambioPrimerPlano(true)

        assertEquals(
            "La identidad debe consultarse en cada decisión, no guardarse una copia",
            2,
            consultas
        )
    }

    @Test
    fun elCoordinadorNoDependeDeCompose() {
        // El coordinador es una clase de Kotlin puro: se puede usar (y
        // probar) sin recomposición, sin Activity y sin Compose. Solo recibe
        // funciones y valores.
        val constructores = CoordinadorSondeoAvisos::class.java.declaredConstructors.toList()

        assertEquals(
            "El coordinador debe tener un único constructor",
            1,
            constructores.size
        )
        assertTrue(
            "El coordinador no debe depender de Compose",
            constructores.all { constructor ->
                constructor.parameterTypes.none { it.name.contains("compose") }
            }
        )
    }

    /** Deja al coordinador en primer plano y con el sondeo en marcha. */
    private fun coordinadorEnPrimerPlanoSondeando() {
        acciones.clear()
        coordinador.onCambioPrimerPlano(true)
        acciones.clear()
    }
}
