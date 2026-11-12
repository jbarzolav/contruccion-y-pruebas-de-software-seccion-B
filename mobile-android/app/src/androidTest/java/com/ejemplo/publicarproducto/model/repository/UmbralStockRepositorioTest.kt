package com.ejemplo.publicarproducto.model.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.local.EntidadUmbralStock
import com.ejemplo.publicarproducto.model.local.EstadosOperacion
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.local.TiposOperacion
import com.ejemplo.publicarproducto.model.UmbralStockResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
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
 * HU 58 - Mejora técnica Offline First: umbrales de stock y avisos de stock bajo.
 *
 * Verifica el ciclo real contra el servidor local de pruebas:
 * - el panel real del backend se guarda en Room;
 * - el cambio de umbral se guarda en el dispositivo y se encola en la cola
 *   persistente EXISTENTE (sin duplicar operaciones);
 * - la confirmación del servidor aplica el umbral y limpia el pendiente;
 * - los avisos de stock bajo se leen de la MISMA tabla de notificaciones
 *   (tipo STOCK_BAJO) combinados con el umbral vigente;
 * - los datos de un usuario nunca se mezclan con los de otro.
 */
@RunWith(AndroidJUnit4::class)
class UmbralStockRepositorioTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var servidor: ServidorDePruebas
    private lateinit var cola: ColaOperacionesRepositorio
    private lateinit var notificaciones: NotificacionesRepositorio
    private lateinit var repositorio: UmbralStockRepositorio

    private val usuarioId = 1L

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        servidor = ServidorDePruebas()
        cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        notificaciones = NotificacionesRepositorio(bd, servidor.api, cola)
        repositorio = UmbralStockRepositorio(bd, servidor.api, cola, notificaciones)
    }

    @After
    fun tearDown() {
        bd.close()
        servidor.cerrar()
    }

    /** Panel de umbrales tal y como lo devuelve el backend. */
    private fun jsonUmbrales(stock: Int = 4, umbral: Int = 5) = """
        [
          {
            "idProducto": 7,
            "nombre": "Café molido",
            "imagenUrl": null,
            "stock": $stock,
            "umbral": $umbral,
            "stockBajo": true,
            "alertaNotificada": true,
            "fechaActualizacion": "2024-05-01T10:15:30"
          }
        ]
    """.trimIndent()

    /** Aviso de stock bajo dentro de la bandeja EXISTENTE de notificaciones. */
    private fun jsonBandejaStockBajo() = """
        {
          "notificaciones": [
            {
              "idNotificacion": 30,
              "tipo": "STOCK_BAJO",
              "resumen": "Stock bajo en «Café molido»: quedan 4 unidades y el umbral configurado es 5.",
              "fecha": "2024-05-01T10:15:30",
              "estado": "PENDIENTE",
              "idPedido": null,
              "enlace": "/mis-publicaciones#producto-7",
              "idConversacion": null,
              "fechaLectura": null,
              "productoId": 7
            }
          ],
          "pagina": 0,
          "tamano": 20,
          "totalElementos": 1,
          "totalPaginas": 1,
          "tieneSiguiente": false,
          "pendientes": 1
        }
    """.trimIndent()

    /** Inserta una publicación con su umbral oficial en la copia local. */
    private suspend fun guardarPublicacion(idProducto: Long = 7L, umbral: Int = 5) {
        bd.umbralStockDao().guardarUno(
            EntidadUmbralStock(
                usuarioId = usuarioId,
                idProducto = idProducto,
                nombre = "Café molido",
                imagenUrl = null,
                stock = 4,
                umbral = umbral,
                stockBajo = true,
                alertaNotificada = false,
                fechaActualizacion = null,
                umbralPendienteLocal = null,
                fechaSincronizacion = 1L
            )
        )
    }

    @Test
    fun elRefrescoGuardaElPanelRealDeUmbralesEnRoom() = runTest {
        servidor.responder(200, jsonUmbrales())

        val resultado = repositorio.refrescarUmbrales(usuarioId)
        assertTrue(resultado is ResultadoApi.Exito)

        val panel = repositorio.observarUmbrales(usuarioId).first()
        assertEquals(1, panel.size)
        assertEquals(7L, panel.first().idProducto)
        assertEquals("Café molido", panel.first().nombre)
        assertEquals(4, panel.first().stock)
        assertEquals(5, panel.first().umbral)
        assertTrue(panel.first().stockBajo == true)
        assertTrue(panel.first().alertaNotificada == true)
    }

    @Test
    fun elCambioDeUmbralSeGuardaEnElDispositivoYSeEncolaEnLaColaExistente() = runTest {
        guardarPublicacion()

        repositorio.registrarUmbralPendienteLocal(usuarioId, 7L, 2)
        repositorio.encolarUmbral(usuarioId, 7L, 2)

        // El valor oficial del servidor NO se pisa: solo queda pendiente.
        val registro = bd.umbralStockDao().buscar(usuarioId, 7L)
        assertNotNull(registro)
        assertEquals(5, registro!!.umbral)
        assertEquals(2, registro.umbralPendienteLocal)

        val pendientes = repositorio.observarPendientes(usuarioId).first()
        assertEquals(mapOf(7L to 2), pendientes)

        // La operación vive en la cola persistente EXISTENTE.
        val operaciones = cola.observarPendientes(usuarioId).first()
        assertEquals(1, operaciones.size)
        assertEquals(TiposOperacion.ACTUALIZAR_UMBRAL_STOCK, operaciones.first().tipoOperacion)
        assertEquals(EstadosOperacion.PENDIENTE, operaciones.first().estado)
        assertEquals("7", operaciones.first().recursoId)
    }

    @Test
    fun encolarElMismoUmbralNoDuplicaLaOperacion() = runTest {
        guardarPublicacion()

        val primera = repositorio.encolarUmbral(usuarioId, 7L, 2)
        val segunda = repositorio.encolarUmbral(usuarioId, 7L, 2)

        assertEquals(primera.id, segunda.id)
        assertEquals(1, cola.observarPendientes(usuarioId).first().size)
    }

    @Test
    fun laConfirmacionDelServidorAplicaElUmbralYLimpiaElPendiente() = runTest {
        guardarPublicacion()
        repositorio.registrarUmbralPendienteLocal(usuarioId, 7L, 2)

        repositorio.aplicarUmbralServidor(
            usuarioId = usuarioId,
            cuerpo = UmbralStockResponse(
                idProducto = 7L,
                nombre = "Café molido",
                imagenUrl = null,
                stock = 8,
                umbral = 2,
                stockBajo = false,
                alertaNotificada = false,
                fechaActualizacion = "2024-05-02T08:00:00"
            )
        )

        val registro = bd.umbralStockDao().buscar(usuarioId, 7L)
        assertNotNull(registro)
        assertEquals(2, registro!!.umbral)
        assertEquals(8, registro.stock)
        assertTrue(!registro.stockBajo)
        // La propuesta local se descarta: el servidor ya confirmó el valor.
        assertNull(registro.umbralPendienteLocal)
        assertTrue(repositorio.observarPendientes(usuarioId).first().isEmpty())
    }

    @Test
    fun elRechazoDelServidorLimpiaElUmbralPendiente() = runTest {
        guardarPublicacion()
        repositorio.registrarUmbralPendienteLocal(usuarioId, 7L, 2)

        repositorio.limpiarUmbralPendiente(usuarioId, 7L)

        val registro = bd.umbralStockDao().buscar(usuarioId, 7L)
        assertNotNull(registro)
        assertNull(registro!!.umbralPendienteLocal)
        // El valor oficial del servidor sigue siendo el vigente.
        assertEquals(5, registro.umbral)
    }

    @Test
    fun losAvisosDeStockBajoCombinanLaBandejaExistenteConLosUmbrales() = runTest {
        servidor.responder(200, jsonUmbrales())
        repositorio.refrescarUmbrales(usuarioId)

        servidor.responder(200, jsonBandejaStockBajo())
        val resultado = repositorio.refrescarAvisos(usuarioId)
        assertTrue(resultado is ResultadoApi.Exito)

        val avisos = repositorio.observarAvisos(usuarioId).first()
        assertEquals(1, avisos.size)
        assertEquals(30L, avisos.first().idNotificacion)
        assertEquals(7L, avisos.first().idProducto)
        assertEquals("Café molido", avisos.first().nombre)
        assertEquals(4, avisos.first().stockActual)
        assertEquals(5, avisos.first().umbral)

        // El aviso vive en la tabla de notificaciones existente: no hay una
        // bandeja paralela para el stock.
        val aviso = bd.notificacionDao().buscar(usuarioId, 30L)
        assertNotNull(aviso)
        assertEquals("STOCK_BAJO", aviso!!.tipo)
        assertEquals(7L, aviso.productoId)
    }

    @Test
    fun losAvisosSeLeenDeLaBandejaFiltradaPorTipoStockBajo() = runTest {
        servidor.responder(200, jsonUmbrales())
        repositorio.refrescarUmbrales(usuarioId)

        servidor.responder(200, jsonBandejaStockBajo())
        repositorio.refrescarAvisos(usuarioId)

        // La bandeja se pide con el filtro de tipo: no se crea una bandeja nueva.
        val rutas = servidor.rutasRecibidas()
        assertTrue(
            "La bandeja debe filtrarse por el tipo STOCK_BAJO: $rutas",
            rutas.any { it.contains("STOCK_BAJO") }
        )
    }

    @Test
    fun unErrorDelServidorNoPisaLosUmbralesGuardados() = runTest {
        servidor.responder(200, jsonUmbrales())
        repositorio.refrescarUmbrales(usuarioId)

        servidor.responderError(500, "Error interno del servidor.")
        val resultado = repositorio.refrescarUmbrales(usuarioId)
        assertTrue(resultado is ResultadoApi.Error)

        val panel = repositorio.observarUmbrales(usuarioId).first()
        assertEquals(1, panel.size)
        assertEquals(5, panel.first().umbral)
    }

    @Test
    fun losUmbralesDeUnUsuarioNoSeMezclanConLosDeOtro() = runTest {
        servidor.responder(200, jsonUmbrales())
        repositorio.refrescarUmbrales(1L)

        servidor.responder(200, jsonUmbrales(stock = 12, umbral = 3))
        repositorio.refrescarUmbrales(2L)

        assertEquals(4, repositorio.observarUmbrales(1L).first().first().stock)
        assertEquals(12, repositorio.observarUmbrales(2L).first().first().stock)

        // La cola tampoco se mezcla entre usuarios.
        repositorio.encolarUmbral(1L, 7L, 2)
        assertEquals(1, cola.observarPendientes(1L).first().size)
        assertEquals(0, cola.observarPendientes(2L).first().size)
    }

    @Test
    fun alCerrarSesionSeBorraLosUmbralesLocalesDelUsuario() = runTest {
        servidor.responder(200, jsonUmbrales())
        repositorio.refrescarUmbrales(usuarioId)

        repositorio.borrarUsuario(usuarioId)

        assertTrue(repositorio.observarUmbrales(usuarioId).first().isEmpty())
        assertNull(bd.umbralStockDao().buscar(usuarioId, 7L))
    }
}
