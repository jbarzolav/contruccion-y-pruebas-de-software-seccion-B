package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.mapper.Mapeadores
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.Ambitos
import com.ejemplo.publicarproducto.model.local.EntidadNotificacion
import com.ejemplo.publicarproducto.model.local.NotificacionDao
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.model.sync.ProgramadorSincronizacion
import com.ejemplo.publicarproducto.testing.ContenedorDePruebas
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.ejemplo.publicarproducto.testing.esperarCondicion
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo D-4 - `PedidosRecibidosViewModel` con la copia local real (HU 48/HU 49).
 *
 * Comprueba la migración de D-4 con Room en memoria: el ViewModel obtiene
 * los avisos del REPOSITORIO (`NotificacionesRepositorio.observarAvisos`),
 * el repositorio observa la copia local, los cambios locales producen nuevas
 * emisiones, el mapeo y el orden de los avisos se conservan y el ViewModel no
 * sostiene ningún DAO ni la base de datos.
 */
@RunWith(AndroidJUnit4::class)
class PedidosRecibidosViewModelTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas

    private val usuarioId = 1L

    /**
     * ViewModels creados por la prueba.
     *
     * El ViewModel observa Room desde `init`, así que su colección sigue
     * viva aunque la prueba termine. Si la base se cerrara con el colector
     * todavía activo, Room lanzaría "Cannot perform this operation because
     * the connection pool has been closed" y el proceso de pruebas se caería.
     * Por eso se cancela el alcance ANTES de cerrar la base.
     */
    private val viewModels = mutableListOf<PedidosRecibidosViewModel>()

    /** Crea un ViewModel registrado para cancelar su alcance al terminar. */
    private fun nuevoViewModel(): PedidosRecibidosViewModel {
        val model = PedidosRecibidosViewModel()
        viewModels += model
        return model
    }

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesion = SessionManager(contexto)
        sesion.invalidarSesion()
        sesion.guardarSesion(token = "token-prueba", id = usuarioId, correo = "vendedor@pulgatec.pe")
        servidor = ServidorDePruebas()
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))
    }

    @After
    fun tearDown() {
        // Primero se detienen los colectores de Room y después se cierra la
        // base: al revés, Room consultaría una base ya cerrada.
        viewModels.forEach { model -> model.cancelarAlcance() }
        viewModels.clear()
        ProgramadorSincronizacion.cancelar(contexto, usuarioId)
        ProveedorDatos.fijar(null)
        sesion.invalidarSesion()
        bd.close()
        servidor.cerrar()
    }

    /** Espera en tiempo real a que se cumpla la condición (hilo principal). */
    private fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
    }

    /** Aviso en la copia local, tal como lo guarda la sincronización. */
    private fun entidad(
        id: Long,
        fecha: String,
        tipo: String? = "NUEVA_COMPRA",
        resumen: String? = "Nueva compra del pedido 55",
        idPedido: Long? = 55L
    ) = EntidadNotificacion(
        usuarioId = usuarioId,
        idNotificacion = id,
        tipo = tipo,
        resumen = resumen,
        fecha = fecha,
        estado = "PENDIENTE",
        idPedido = idPedido,
        enlace = "/pedidos/$idPedido",
        idConversacion = null,
        fechaLectura = null,
        fechaSincronizacion = 1_000L,
        productoId = null,
        pendienteLecturaLocal = false
    )

    /** Guarda avisos en la copia local (como haría la sincronización). */
    private fun guardar(vararg avisos: EntidadNotificacion) = runBlocking {
        bd.notificacionDao().guardarTodas(avisos.toList())
    }

    private val jsonVentas = """
        [
          {
            "idPedido": 55,
            "fecha": "2024-05-01T10:15:30",
            "estado": "REGISTRADO",
            "fechaActualizacion": "2024-05-01T10:15:30",
            "total": 120.00,
            "productos": [
              {
                "idProducto": 901,
                "nombreProducto": "Mouse",
                "cantidad": 1,
                "precioUnitario": 120.00,
                "subtotal": 120.00
              }
            ],
            "estadosPermitidos": ["EN_PROCESO"]
          }
        ]
    """.trimIndent()

    @Test
    fun elEstadoInicialNoTienePedidosNiAvisos() {
        val model = nuevoViewModel()

        assertTrue(model.pedidos.isEmpty())
        assertTrue(model.notificaciones.isEmpty())
        assertTrue(model.estadosPendientes.isEmpty())
        assertFalse(model.cargando)
        assertNull(model.error)
        assertNull(model.mensaje)
        assertNull(model.guardandoId)
        assertNull(model.pedidoEnfocadoId)
    }

    @Test
    fun losAvisosDelViewModelSonLosDelRepositorio() {
        // 1. El ViewModel obtiene los avisos mediante el repositorio: el
        //    repositorio lee la copia local y entrega los mismos DTO.
        val model = nuevoViewModel()
        guardar(
            entidad(10L, "2024-05-01T10:15:30"),
            entidad(20L, "2024-05-02T10:15:30")
        )

        esperar { model.notificaciones.size == 2 }

        val delRepositorio = runBlocking {
            ProveedorDatos.actual.notificaciones.observarAvisos(usuarioId).first()
        }
        assertEquals(delRepositorio, model.notificaciones)
    }

    @Test
    fun elRepositorioObservaLaCopiaLocalYElViewModelSeActualiza() {
        // 2 y 3. El repositorio observa Room y los cambios locales producen
        //         nuevas emisiones que llegan al estado del ViewModel.
        val model = nuevoViewModel()
        guardar(entidad(10L, "2024-05-01T10:15:30"))
        esperar { model.notificaciones.size == 1 }

        guardar(entidad(30L, "2024-05-03T10:15:30"))
        esperar { model.notificaciones.size == 2 }

        assertEquals(listOf(30L, 10L), model.notificaciones.map { it.idNotificacion })
    }

    @Test
    fun elMapeoDeLosAvisosSeConserva() {
        // 4. El mapeo entidad -> DTO es el mismo de antes: el repositorio
        //    entrega `Mapeadores.aDto` sin cambios.
        val model = nuevoViewModel()
        val aviso = entidad(10L, "2024-05-01T10:15:30")
        guardar(aviso)

        esperar { model.notificaciones.size == 1 }

        assertEquals(Mapeadores.aDto(aviso), model.notificaciones.first())
        val recibido = model.notificaciones.first()
        assertEquals("NUEVA_COMPRA", recibido.tipo)
        assertEquals("Nueva compra del pedido 55", recibido.resumen)
        assertEquals("2024-05-01T10:15:30", recibido.fecha)
        assertEquals("PENDIENTE", recibido.estado)
        assertEquals(55L, recibido.idPedido)
        assertEquals("/pedidos/55", recibido.enlace)
        assertNull(recibido.idConversacion)
        assertNull(recibido.productoId)
    }

    @Test
    fun elOrdenDeLosAvisosNoCambia() {
        // 5. El orden estable del DAO (`fecha DESC, idNotificacion DESC`) se
        //    conserva en el estado del ViewModel.
        val model = nuevoViewModel()
        guardar(
            entidad(10L, "2024-05-01T10:15:30"),
            entidad(30L, "2024-05-03T10:15:30"),
            entidad(20L, "2024-05-02T10:15:30")
        )

        esperar { model.notificaciones.size == 3 }

        assertEquals(listOf(30L, 20L, 10L), model.notificaciones.map { it.idNotificacion })
    }

    @Test
    fun elEstadoQueConsumeLaPantallaSeActualiza() {
        // 6. La pantalla muestra `viewModel.notificaciones`: cuando la copia
        //    local cambia, el estado que consume la pantalla se actualiza.
        val model = nuevoViewModel()
        esperar { model.notificaciones.isEmpty() }

        guardar(entidad(10L, "2024-05-01T10:15:30"))
        esperar { model.notificaciones.size == 1 }

        assertEquals("Nueva compra del pedido 55", model.notificaciones.first().resumen)
        assertTrue(model.notificaciones.first().tipo == "NUEVA_COMPRA")
    }

    @Test
    fun losPedidosSeCarganDesdeElBackend() {
        servidor.responder(200, jsonVentas)
        val model = nuevoViewModel()

        model.cargar()

        esperar { model.pedidos.isNotEmpty() }
        assertEquals(55L, model.pedidos.first().idPedido)
        assertEquals("Mouse", model.pedidos.first().productos.first().nombreProducto)
        assertEquals(listOf("EN_PROCESO"), model.pedidos.first().estadosPermitidos)
        assertFalse(model.cargando)
        assertNull(model.error)
        // El ViewModel pide los pedidos al endpoint real de HU 48.
        val peticiones = servidor.peticiones()
        val peticion = peticiones.firstOrNull { it.path == "/api/vendedores/me/pedidos" }
        assertNotNull(
            "Debe pedir los pedidos al backend: ${peticiones.map { it.path }}",
            peticion
        )
        assertEquals("GET", peticion!!.method)
    }

    @Test
    fun unErrorAlCargarMuestraElMensajeDelBackend() {
        servidor.responderError(400, "No se pudieron obtener los pedidos")
        val model = nuevoViewModel()

        model.cargar()

        esperar { model.error != null }
        assertEquals("No se pudieron obtener los pedidos", model.error)
        assertFalse(model.cargando)
    }

    @Test
    fun laPropuestaDeEstadoQuedaPendienteEnElDispositivo() {
        servidor.responder(200, jsonVentas)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        model.actualizarEstado(55L, "EN_PROCESO")

        esperar { model.mensaje != null }
        assertTrue(
            "Mensaje: ${model.mensaje}",
            model.mensaje!!.contains("Pendiente de sincronizar con el servidor")
        )
        assertNull(model.error)
        assertEquals("EN_PROCESO", model.estadosPendientes[55L])
        // El estado oficial del servidor no se toca hasta que lo confirme.
        val oficial = runBlocking {
            bd.pedidoDao().buscarPedido(usuarioId, 55L, Ambitos.VENTAS)
        }
        assertNotNull(oficial)
        assertEquals("REGISTRADO", oficial!!.estado)
    }

    @Test
    fun elViewModelNoSostieneElDaoNiLaBaseDeDatos() {
        // 7. El ViewModel no conserva ninguna referencia al DAO ni a la base
        //    de datos: sus datos vienen del repositorio.
        val tipos = mutableSetOf<Class<*>>()
        var clase: Class<*>? = PedidosRecibidosViewModel::class.java
        while (clase != null && clase != Any::class.java) {
            clase.declaredFields.forEach { tipos.add(it.type) }
            clase = clase.superclass
        }

        assertFalse(
            "El ViewModel no debe guardar un DAO de notificaciones",
            tipos.contains(NotificacionDao::class.java)
        )
        assertFalse(
            "El ViewModel no debe guardar la base de datos",
            tipos.contains(PulgaTecDatabase::class.java)
        )
    }
}
