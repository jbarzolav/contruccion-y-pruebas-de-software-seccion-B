package com.ejemplo.publicarproducto.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HU 57 - Mejora técnica Offline First: panel de métricas de ventas.
 *
 * Verifica el ciclo real contra el servidor local de pruebas:
 * - la respuesta del backend se guarda en Room y la interfaz la observa;
 * - solo se conserva el ÚLTIMO resultado válido (la tabla se sobrescribe);
 * - un error del servidor NO pisa el último panel correcto;
 * - los datos de un usuario nunca se mezclan con los de otro.
 */
@RunWith(AndroidJUnit4::class)
class MetricasRepositorioTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var servidor: ServidorDePruebas
    private lateinit var repositorio: MetricasRepositorio

    private val usuarioId = 1L

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        servidor = ServidorDePruebas()
        repositorio = MetricasRepositorio(bd, servidor.api)
    }

    @After
    fun tearDown() {
        bd.close()
        servidor.cerrar()
    }

    /** Panel de métricas tal y como lo devuelve el backend. */
    private fun jsonMetricas(
        ingresos: String = "1200.50",
        pedidos: Long = 4L,
        promedio: String = "300.12",
        desde: String = "2024-05-01",
        hasta: String = "2024-05-31"
    ) = """
        {
          "desde": "$desde",
          "hasta": "$hasta",
          "desdeAnterior": "2024-04-01",
          "hastaAnterior": "2024-04-30",
          "compararPeriodoAnterior": true,
          "periodoActual": {
            "ingresosConfirmados": $ingresos,
            "cantidadPedidos": $pedidos,
            "promedioPorPedido": $promedio
          },
          "periodoAnterior": {
            "ingresosConfirmados": 800.00,
            "cantidadPedidos": 2,
            "promedioPorPedido": 400.00
          },
          "comparativaIngresos": {
            "diferenciaAbsoluta": 400.50,
            "variacionPorcentual": 50.06,
            "tendencia": "AUMENTO"
          },
          "comparativaPedidos": {
            "diferenciaAbsoluta": 2,
            "variacionPorcentual": 100.00,
            "tendencia": "AUMENTO"
          },
          "comparativaPromedio": {
            "diferenciaAbsoluta": -99.88,
            "variacionPorcentual": -24.97,
            "tendencia": "DISMINUCION"
          },
          "productosMasVendidos": [
            {
              "idProducto": 7,
              "nombre": "Café molido",
              "imagenUrl": null,
              "unidadesVendidas": 3,
              "importeAtribuido": 60.00
            }
          ]
        }
    """.trimIndent()

    @Test
    fun elRefrescoGuardaElPanelRealDelServidorEnRoom() = runTest {
        servidor.responder(200, jsonMetricas())

        val resultado = repositorio.refrescar(usuarioId, "2024-05-01", "2024-05-31", true)
        assertTrue(resultado is ResultadoApi.Exito)

        val panel = repositorio.ultimoResultado(usuarioId)
        assertNotNull("El panel debe quedar guardado en Room", panel)
        assertEquals("2024-05-01", panel!!.desde)
        assertEquals("2024-05-31", panel.hasta)
        assertEquals(4L, panel.periodoActual!!.cantidadPedidos)
        assertEquals("1200.50", panel.periodoActual!!.ingresosConfirmados.toString())
        assertEquals("AUMENTO", panel.comparativaIngresos!!.tendencia)
        assertEquals(1, panel.productosMasVendidos!!.size)
        assertEquals("Café molido", panel.productosMasVendidos!!.first().nombre)
        assertEquals(3L, panel.productosMasVendidos!!.first().unidadesVendidas)
    }

    @Test
    fun elPanelSeObservaDesdeLaCopiaLocal() = runTest {
        servidor.responder(200, jsonMetricas())
        repositorio.refrescar(usuarioId, "2024-05-01", "2024-05-31", true)

        val observado = repositorio.observar(usuarioId).first()
        assertNotNull(observado)
        assertEquals(4L, observado!!.periodoActual!!.cantidadPedidos)
    }

    @Test
    fun soloSeGuardaElUltimoResultadoValido() = runTest {
        servidor.responder(200, jsonMetricas(ingresos = "1200.50", pedidos = 4L))
        repositorio.refrescar(usuarioId, "2024-05-01", "2024-05-31", true)

        // Segundo panel: sobrescribe el anterior (no hay histórico).
        servidor.responder(
            200,
            jsonMetricas(ingresos = "800.00", pedidos = 2L, desde = "2024-06-01", hasta = "2024-06-30")
        )
        repositorio.refrescar(usuarioId, "2024-06-01", "2024-06-30", true)

        val panel = repositorio.ultimoResultado(usuarioId)
        assertNotNull(panel)
        assertEquals("800.00", panel!!.periodoActual!!.ingresosConfirmados.toString())
        assertEquals(2L, panel.periodoActual!!.cantidadPedidos)
        assertEquals("2024-06-01", panel.desde)

        // El periodo guardado corresponde al último panel, no al anterior.
        val entidad = bd.metricasDao().buscar(usuarioId)
        assertNotNull(entidad)
        assertEquals(
            MetricasRepositorio.clavePeriodo("2024-06-01", "2024-06-30", true),
            entidad!!.clavePeriodo
        )
    }

    @Test
    fun unErrorDelServidorNoPisaElUltimoPanelValido() = runTest {
        servidor.responder(200, jsonMetricas())
        repositorio.refrescar(usuarioId, "2024-05-01", "2024-05-31", true)

        // El servidor falla: la copia local debe seguir intacta.
        servidor.responderError(500, "Error interno del servidor.")
        val resultado = repositorio.refrescar(usuarioId, "2024-06-01", "2024-06-30", true)
        assertTrue(resultado is ResultadoApi.Error)

        val panel = repositorio.ultimoResultado(usuarioId)
        assertNotNull("El último panel válido debe conservarse", panel)
        assertEquals("2024-05-01", panel!!.desde)
        assertEquals(4L, panel.periodoActual!!.cantidadPedidos)
    }

    @Test
    fun sinRedElPanelLocalSigueDisponible() = runTest {
        servidor.responder(200, jsonMetricas())
        repositorio.refrescar(usuarioId, "2024-05-01", "2024-05-31", true)

        servidor.apagar()
        val resultado = repositorio.refrescar(usuarioId, "2024-06-01", "2024-06-30", true)
        assertTrue(resultado is ResultadoApi.Error)
        assertTrue((resultado as ResultadoApi.Error).temporal)

        val panel = repositorio.ultimoResultado(usuarioId)
        assertNotNull(panel)
        assertEquals(4L, panel!!.periodoActual!!.cantidadPedidos)
    }

    @Test
    fun losDatosDeUnUsuarioNoSeMezclanConLosDeOtro() = runTest {
        servidor.responder(200, jsonMetricas(pedidos = 4L))
        repositorio.refrescar(1L, "2024-05-01", "2024-05-31", true)

        servidor.responder(200, jsonMetricas(pedidos = 9L))
        repositorio.refrescar(2L, "2024-05-01", "2024-05-31", true)

        assertEquals(4L, repositorio.ultimoResultado(1L)!!.periodoActual!!.cantidadPedidos)
        assertEquals(9L, repositorio.ultimoResultado(2L)!!.periodoActual!!.cantidadPedidos)

        // Cada usuario tiene su propia fila: nadie ve la copia de otro.
        assertNotNull(bd.metricasDao().buscar(1L))
        assertNotNull(bd.metricasDao().buscar(2L))
    }

    @Test
    fun alCerrarSesionSeBorraElPanelLocalDelUsuario() = runTest {
        servidor.responder(200, jsonMetricas())
        repositorio.refrescar(usuarioId, "2024-05-01", "2024-05-31", true)

        repositorio.borrarUsuario(usuarioId)

        assertNull(bd.metricasDao().buscar(usuarioId))
        assertNull(repositorio.ultimoResultado(usuarioId))
    }
}
