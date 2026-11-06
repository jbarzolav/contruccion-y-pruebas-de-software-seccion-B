package com.ejemplo.publicarproducto.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ejemplo.publicarproducto.data.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.data.ContenedorDatos
import com.ejemplo.publicarproducto.data.MonitorConectividad
import com.ejemplo.publicarproducto.data.NotificacionesRepositorio
import com.ejemplo.publicarproducto.data.PedidosRepositorio
import com.ejemplo.publicarproducto.data.ProveedorDatos
import com.ejemplo.publicarproducto.local.EstadosOperacion
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.local.TiposOperacion
import com.ejemplo.publicarproducto.session.SessionManager
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Mejora tecnica Offline First - integracion con WorkManager.
 *
 * Verifica que la sincronizacion se programe como trabajo unico, que exija
 * conexion de red, que el trabajador procese realmente la cola persistente y
 * que un error temporal provoque un reintento (y no una perdida de la
 * operacion).
 */
@RunWith(AndroidJUnit4::class)
class SincronizacionWorkerTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var servidor: ServidorDePruebas
    private lateinit var sesion: SessionManager
    private lateinit var cola: ColaOperacionesRepositorio

    /** Usuario de las pruebas del trabajador (aislado de las de la aplicacion). */
    private val usuarioId = 990001L

    /** Usuario de las pruebas de programacion (sin sesion: no hay interferencia). */
    private val usuarioSinSesion = 990002L

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        servidor = ServidorDePruebas()
        sesion = SessionManager(contexto)
        cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
    }

    @After
    fun tearDown() {
        sesion.invalidarSesion()
        ProgramadorSincronizacion.cancelar(contexto, usuarioId)
        ProgramadorSincronizacion.cancelar(contexto, usuarioSinSesion)
        ProveedorDatos.fijar(null)
        bd.close()
        servidor.cerrar()
    }

    /** Contenedor de datos de pruebas: usa el servidor HTTP local. */
    private fun contenedor(): ContenedorDatos {
        val notificaciones = NotificacionesRepositorio(bd, servidor.api, cola)
        val pedidos = PedidosRepositorio(bd, servidor.api, cola)
        return ContenedorDatos(
            contexto = contexto,
            bd = bd,
            cola = cola,
            notificaciones = notificaciones,
            pedidos = pedidos,
            metadatos = bd.metadatoSincronizacionDao(),
            conectividad = MonitorConectividad(contexto),
            sesion = sesion,
            sincronizacion = ServicioSincronizacion(
                bd = bd,
                api = servidor.api,
                cola = cola,
                notificaciones = notificaciones,
                pedidos = pedidos,
                metadatos = bd.metadatoSincronizacionDao(),
                sesion = sesion
            )
        )
    }

    private fun jsonBandeja(estado: String = "LEIDA", pendientes: Long = 0L) = """
        {
          "notificaciones": [
            {
              "idNotificacion": 10,
              "tipo": "NUEVO_MENSAJE",
              "resumen": "El cliente te envio un mensaje.",
              "fecha": "2024-05-01T10:15:30",
              "estado": "$estado",
              "idPedido": null,
              "enlace": "/consultas?c=1",
              "idConversacion": 7,
              "fechaLectura": "2024-05-02T08:00:00"
            }
          ],
          "pagina": 0,
          "tamano": 20,
          "totalElementos": 1,
          "totalPaginas": 1,
          "tieneSiguiente": false,
          "pendientes": $pendientes
        }
    """.trimIndent()

    @Test
    fun laSincronizacionSeProgramaComoTrabajoUnicoQueExigeRed() = runTest {
        ProgramadorSincronizacion.programar(contexto, usuarioSinSesion)

        val trabajos = WorkManager.getInstance(contexto)
            .getWorkInfosForUniqueWork(ProgramadorSincronizacion.nombreTrabajo(usuarioSinSesion))
            .get(10, TimeUnit.SECONDS)

        assertNotNull(trabajos)
        assertEquals(1, trabajos!!.size)
        val trabajo = trabajos.first()
        assertEquals(NetworkType.CONNECTED, trabajo.constraints.requiredNetworkType)
        assertTrue(trabajo.tags.contains(ProgramadorSincronizacion.ETIQUETA))
        assertTrue(trabajo.tags.contains(ProgramadorSincronizacion.nombreTrabajo(usuarioSinSesion)))
        assertEquals(WorkInfo.State.ENQUEUED, trabajo.state)
    }

    @Test
    fun alCerrarSesionSeCancelaElTrabajoDeSincronizacion() = runTest {
        ProgramadorSincronizacion.programar(contexto, usuarioSinSesion)
        ProgramadorSincronizacion.cancelar(contexto, usuarioSinSesion)

        val trabajos = WorkManager.getInstance(contexto)
            .getWorkInfosForUniqueWork(ProgramadorSincronizacion.nombreTrabajo(usuarioSinSesion))
            .get(10, TimeUnit.SECONDS)

        assertNotNull(trabajos)
        assertTrue(trabajos!!.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun elTrabajadorProcesaLaColaYConfirmaConElServidor() = runTest {
        sesion.guardarSesion(token = "token-prueba", id = usuarioId, correo = "prueba@pulgatec.pe")
        ProveedorDatos.fijar(contenedor())

        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "10",
            payload = null,
            claveUnica = "notificacion:10"
        )

        // Respuesta de la lectura + refresco de bandeja, ventas y compras.
        servidor.responder(
            200,
            """{"idNotificacion":10,"estado":"LEIDA","fechaLectura":"2024-05-02T08:00:00","pendientes":0}"""
        )
        servidor.responder(200, jsonBandeja())
        servidor.responder(200, "[]")
        servidor.responder(200, "[]")

        val trabajador = TestListenableWorkerBuilder<SincronizacionWorker>(contexto)
            .setInputData(
                workDataOf(ProgramadorSincronizacion.CLAVE_USUARIO_ID to usuarioId)
            )
            .build()

        val resultado = trabajador.doWork()

        assertTrue(resultado is androidx.work.ListenableWorker.Result.Success)
        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.CONFIRMADA, operacion.estado)
        assertTrue(cola.observarPendientes(usuarioId).first().isEmpty())
    }

    @Test
    fun unErrorTemporalHaceQueElTrabajadorPidaUnReintento() = runTest {
        sesion.guardarSesion(token = "token-prueba", id = usuarioId, correo = "prueba@pulgatec.pe")
        ProveedorDatos.fijar(contenedor())

        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "10",
            payload = null,
            claveUnica = "notificacion:10"
        )

        servidor.responderError(503, "El servicio no esta disponible.")

        val trabajador = TestListenableWorkerBuilder<SincronizacionWorker>(contexto)
            .setInputData(
                workDataOf(ProgramadorSincronizacion.CLAVE_USUARIO_ID to usuarioId)
            )
            .build()

        val resultado = trabajador.doWork()

        assertTrue(resultado is androidx.work.ListenableWorker.Result.Retry)
        // La operacion NO se pierde: sigue pendiente para el proximo intento.
        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.PENDIENTE, operacion.estado)
        assertEquals(1, cola.observarPendientes(usuarioId).first().size)
    }

    @Test
    fun sinSesionElTrabajadorSeDetieneSinIntentarEnviarNada() = runTest {
        sesion.invalidarSesion()
        ProveedorDatos.fijar(contenedor())

        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = "10",
            payload = null,
            claveUnica = "notificacion:10"
        )

        val trabajador = TestListenableWorkerBuilder<SincronizacionWorker>(contexto)
            .setInputData(
                workDataOf(ProgramadorSincronizacion.CLAVE_USUARIO_ID to usuarioId)
            )
            .build()

        val resultado = trabajador.doWork()

        // Sin sesion no se reintenta en bucle: el trabajo termina y la
        // operacion espera a que el usuario vuelva a entrar.
        assertTrue(resultado is androidx.work.ListenableWorker.Result.Success)
        assertEquals(0, servidor.peticionesRecibidas())
        assertEquals(1, cola.observarPendientes(usuarioId).first().size)
    }
}
