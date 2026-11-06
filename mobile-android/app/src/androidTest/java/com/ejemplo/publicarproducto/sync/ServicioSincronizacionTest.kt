package com.ejemplo.publicarproducto.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.data.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.data.NotificacionesRepositorio
import com.ejemplo.publicarproducto.data.PedidosRepositorio
import com.ejemplo.publicarproducto.local.Ambitos
import com.ejemplo.publicarproducto.local.EntidadPedido
import com.ejemplo.publicarproducto.local.EstadosOperacion
import com.ejemplo.publicarproducto.local.EstadosSincronizacion
import com.ejemplo.publicarproducto.local.MAX_INTENTOS_OPERACION
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.local.RecursosSincronizacion
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

/**
 * Mejora técnica Offline First - sincronización de la cola pendiente.
 *
 * Verifica con respuestas HTTP reales (servidor local de pruebas) que:
 * - la confirmación del servidor actualiza la copia local de Room;
 * - un rechazo definitivo no se reintenta y revierte lo local;
 * - un error temporal deja la operación pendiente y pide reintento;
 * - una sesión expirada detiene la sincronización;
 * - un cambio de estado ya aplicado por el servidor no se rechaza (reintento
 *   seguro).
 */
@RunWith(AndroidJUnit4::class)
class ServicioSincronizacionTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var servidor: ServidorDePruebas
    private lateinit var sesion: SessionManager
    private lateinit var cola: ColaOperacionesRepositorio
    private lateinit var servicio: ServicioSincronizacion

    private val usuarioId = 1L

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        servidor = ServidorDePruebas()
        sesion = SessionManager(contexto)
        sesion.guardarSesion(token = "token-prueba", id = usuarioId, correo = "prueba@pulgatec.pe")
        cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        val notificaciones = NotificacionesRepositorio(bd, servidor.api, cola)
        val pedidos = PedidosRepositorio(bd, servidor.api, cola)
        servicio = ServicioSincronizacion(
            bd = bd,
            api = servidor.api,
            cola = cola,
            notificaciones = notificaciones,
            pedidos = pedidos,
            metadatos = bd.metadatoSincronizacionDao(),
            sesion = sesion
        )
    }

    @After
    fun tearDown() {
        sesion.invalidarSesion()
        bd.close()
        servidor.cerrar()
    }

    private fun jsonBandeja(estado: String = "PENDIENTE", pendientes: Long = 1L) = """
        {
          "notificaciones": [
            {
              "idNotificacion": 10,
              "tipo": "NUEVO_MENSAJE",
              "resumen": "El cliente te envió un mensaje.",
              "fecha": "2024-05-01T10:15:30",
              "estado": "$estado",
              "idPedido": null,
              "enlace": "/consultas?c=1",
              "idConversacion": 7,
              "fechaLectura": null
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

    private suspend fun encolaLectura() = cola.encolar(
        usuarioId = usuarioId,
        tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
        recursoId = "10",
        payload = null,
        claveUnica = "notificacion:10"
    )

    private suspend fun guardaBandejaEnRoom() {
        servidor.responder(200, jsonBandeja())
        NotificacionesRepositorio(bd, servidor.api, cola)
            .refrescar(usuarioId, null, null, null, null, 0)
    }

    @Test
    fun laConfirmacionDelServidorActualizaLaCopiaLocal() = runTest {
        guardaBandejaEnRoom()
        encolaLectura()

        servidor.responder(
            200,
            """{"idNotificacion":10,"estado":"LEIDA","fechaLectura":"2024-05-02T08:00:00","pendientes":0}"""
        )
        servidor.responder(200, jsonBandeja(estado = "LEIDA", pendientes = 0L))
        servidor.responder(200, "[]")
        servidor.responder(200, "[]")

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.Exito)

        val aviso = bd.notificacionDao().buscar(usuarioId, 10L)
        assertNotNull(aviso)
        assertEquals("LEIDA", aviso!!.estado)
        assertTrue(!aviso.pendienteLecturaLocal)

        val operaciones = bd.operacionPendienteDao().observarTodas(usuarioId).first()
        assertEquals(EstadosOperacion.CONFIRMADA, operaciones.single().estado)
        assertTrue(cola.observarPendientes(usuarioId).first().isEmpty())
    }

    @Test
    fun elRechazoDefinitivoNoSeReintentaYRevierteLoLocal() = runTest {
        guardaBandejaEnRoom()
        bd.notificacionDao().marcarLecturaLocal(usuarioId, 10L, "2024-05-02T08:00:00")
        encolaLectura()

        servidor.responderError(403, "La notificación pertenece a otro usuario.")
        servidor.responder(200, jsonBandeja(estado = "PENDIENTE", pendientes = 1L))
        servidor.responder(200, "[]")
        servidor.responder(200, "[]")

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.Exito)

        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.RECHAZADA, operacion.estado)
        assertTrue(operacion.ultimoMensajeError!!.contains("otro usuario"))

        // La operación rechazada no vuelve a la cola de pendientes.
        assertTrue(cola.observarPendientes(usuarioId).first().isEmpty())

        // El estado del servidor prevalece: el aviso vuelve a estar pendiente.
        val aviso = bd.notificacionDao().buscar(usuarioId, 10L)
        assertEquals("PENDIENTE", aviso!!.estado)
        assertTrue(!aviso.pendienteLecturaLocal)

        val metadato = bd.metadatoSincronizacionDao().buscar(usuarioId, RecursosSincronizacion.OPERACIONES)
        assertEquals(EstadosSincronizacion.RECHAZADO, metadato!!.estado)
    }

    @Test
    fun unErrorTemporalDejaLaOperacionPendiente() = runTest {
        encolaLectura()

        servidor.responderError(503, "El servicio no está disponible.")

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.Reintentar)

        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.PENDIENTE, operacion.estado)
        assertEquals(1, operacion.intentos)
        assertEquals(1, cola.observarPendientes(usuarioId).first().size)
    }

    @Test
    fun sinRedLaOperacionSiguePendiente() = runTest {
        encolaLectura()
        servidor.apagar()

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.Reintentar)
        assertEquals(1, cola.observarPendientes(usuarioId).first().size)
    }

    @Test
    fun losReintentosSonControladosYLaOperacionTerminaRechazada() = runTest {
        encolaLectura()

        // El servidor no esta disponible de forma temporal en cada intento.
        repeat(MAX_INTENTOS_OPERACION) {
            servidor.responderError(503, "El servicio no está disponible.")
            val resultado = servicio.sincronizar(usuarioId)
            assertTrue(resultado is ResultadoSincronizacion.Reintentar)
        }

        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.RECHAZADA, operacion.estado)
        assertEquals(MAX_INTENTOS_OPERACION, operacion.intentos)
        assertTrue(operacion.ultimoMensajeError!!.contains("Se agotaron los reintentos"))
        // La operacion agotada ya no se reintenta indefinidamente.
        assertTrue(cola.observarPendientes(usuarioId).first().isEmpty())
    }

    @Test
    fun laSesionExpiradaDetieneLaSincronizacion() = runTest {
        encolaLectura()

        servidor.responderError(401, "Sesión requerida.")

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.SesionExpirada)

        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.REQUIERE_SESION, operacion.estado)
    }

    @Test
    fun sinSesionActivaNoSeIntentaSincronizar() = runTest {
        encolaLectura()
        sesion.invalidarSesion()

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.SesionExpirada)
        assertEquals(1, cola.observarPendientes(usuarioId).first().size)
    }

    @Test
    fun elCambioDeEstadoConfirmadoSeReflejaEnRoom() = runTest {
        bd.pedidoDao().guardarPedidos(
            listOf(
                EntidadPedido(
                    usuarioId = usuarioId,
                    idPedido = 55L,
                    ambito = Ambitos.VENTAS,
                    clienteId = null,
                    fecha = "2024-05-01T10:15:30.000000000",
                    total = "120.00",
                    estado = "REGISTRADO",
                    fechaActualizacion = null,
                    estadosPermitidos = "EN_PROCESO",
                    estadoPendienteLocal = "EN_PROCESO",
                    fechaSincronizacion = 1L
                )
            )
        )
        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.ACTUALIZAR_ESTADO_PEDIDO,
            recursoId = "55",
            payload = mapOf("estado" to "EN_PROCESO", "ambito" to Ambitos.VENTAS),
            claveUnica = "pedido:VENTAS:55:EN_PROCESO"
        )

        servidor.responder(
            200,
            """{"idPedido":55,"estadoAnterior":"REGISTRADO","estado":"EN_PROCESO",
               "fechaActualizacion":"2024-05-02T08:00:00","estadosPermitidos":["ENTREGADO"],"historial":[]}"""
        )
        servidor.responder(200, jsonBandeja())
        servidor.responder(200, "[]")
        servidor.responder(200, "[]")

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.Exito)

        val pedido = bd.pedidoDao().buscarPedido(usuarioId, 55L, Ambitos.VENTAS)
        assertEquals("EN_PROCESO", pedido!!.estado)
        assertEquals(null, pedido.estadoPendienteLocal)

        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.CONFIRMADA, operacion.estado)
    }

    @Test
    fun unCambioDeEstadoYaAplicadoNoSeRechazaAlReintentar() = runTest {
        bd.pedidoDao().guardarPedidos(
            listOf(
                EntidadPedido(
                    usuarioId = usuarioId,
                    idPedido = 55L,
                    ambito = Ambitos.VENTAS,
                    clienteId = null,
                    fecha = "2024-05-01T10:15:30.000000000",
                    total = "120.00",
                    estado = "REGISTRADO",
                    fechaActualizacion = null,
                    estadosPermitidos = "EN_PROCESO",
                    estadoPendienteLocal = "EN_PROCESO",
                    fechaSincronizacion = 1L
                )
            )
        )
        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.ACTUALIZAR_ESTADO_PEDIDO,
            recursoId = "55",
            payload = mapOf("estado" to "EN_PROCESO", "ambito" to Ambitos.VENTAS),
            claveUnica = "pedido:VENTAS:55:EN_PROCESO"
        )

        // El servidor responde que el pedido YA está en ese estado: el
        // reintento debe confirmarse, no rechazarse.
        servidor.responderError(400, "El pedido ya se encuentra en estado EN_PROCESO.")
        servidor.responder(200, jsonBandeja())
        servidor.responder(200, "[]")
        servidor.responder(200, "[]")

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.Exito)

        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.CONFIRMADA, operacion.estado)
        assertTrue(cola.observarPendientes(usuarioId).first().isEmpty())
    }

    @Test
    fun unCambioDeEstadoRechazadoSeDescarta() = runTest {
        bd.pedidoDao().guardarPedidos(
            listOf(
                EntidadPedido(
                    usuarioId = usuarioId,
                    idPedido = 55L,
                    ambito = Ambitos.VENTAS,
                    clienteId = null,
                    fecha = "2024-05-01T10:15:30.000000000",
                    total = "120.00",
                    estado = "REGISTRADO",
                    fechaActualizacion = null,
                    estadosPermitidos = "EN_PROCESO",
                    estadoPendienteLocal = "EN_PROCESO",
                    fechaSincronizacion = 1L
                )
            )
        )
        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.ACTUALIZAR_ESTADO_PEDIDO,
            recursoId = "55",
            payload = mapOf("estado" to "CANCELADO", "ambito" to Ambitos.VENTAS),
            claveUnica = "pedido:VENTAS:55:CANCELADO"
        )

        servidor.responderError(400, "No se permite pasar de REGISTRADO a CANCELADO.")
        servidor.responder(200, jsonBandeja())
        servidor.responder(200, "[]")
        servidor.responder(200, "[]")

        val resultado = servicio.sincronizar(usuarioId)
        assertTrue(resultado is ResultadoSincronizacion.Exito)

        val operacion = bd.operacionPendienteDao().observarTodas(usuarioId).first().single()
        assertEquals(EstadosOperacion.RECHAZADA, operacion.estado)
        assertTrue(cola.observarPendientes(usuarioId).first().isEmpty())

        val pedido = bd.pedidoDao().buscarPedido(usuarioId, 55L, Ambitos.VENTAS)
        assertEquals(null, pedido!!.estadoPendienteLocal)
        assertEquals("REGISTRADO", pedido.estado)
    }
}
