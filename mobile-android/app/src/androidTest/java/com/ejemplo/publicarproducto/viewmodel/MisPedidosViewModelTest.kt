package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.FiltrosPedidos
import com.ejemplo.publicarproducto.view.*
import com.ejemplo.publicarproducto.viewmodel.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ejemplo.publicarproducto.model.mapper.Mapeadores
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.NotificacionResponse
import com.ejemplo.publicarproducto.model.session.SessionManager
import com.ejemplo.publicarproducto.testing.ContenedorDePruebas
import com.ejemplo.publicarproducto.testing.ServidorDePruebas
import kotlinx.coroutines.cancel
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
import java.util.concurrent.TimeUnit
import com.ejemplo.publicarproducto.testing.esperarCondicion
import com.ejemplo.publicarproducto.testing.cancelarAlcance

/**
 * Grupo D-3 - MisPedidosViewModel (HU 45, HU 46 y HU 47).
 *
 * Comprueba que el ViewModel ya no toca DAOs ni coordina Retrofit con Room:
 * delega en `PedidosRepositorio` y `NotificacionesRepositorio`, administra
 * el estado de los filtros con `FiltrosPedidos` y devuelve exactamente los
 * mismos textos que la pantalla original:
 * `"Reseña actualizada correctamente."`, `"Error N al consultar pedidos."`,
 * el primer `messages[]` y `"No se pudo actualizar la reseña."`.
 *
 * Usa MockWebServer y Room en memoria: sin backend ni base de datos real.
 */
@RunWith(AndroidJUnit4::class)
class MisPedidosViewModelTest {

    private lateinit var contexto: Context
    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var servidor: ServidorDePruebas

    /**
     * ViewModels creados por la prueba.
     *
     * El ViewModel observa Room desde `init`, así que sucolección sigue
     * viva aunque la prueba termine. Si la base se cerrara con el
     * colector todavía activo, Room lanzaría
     * "Cannot perform this operation because the connection pool has been
     * closed" y el proceso de pruebas se caería. Por eso se cancela el
     * alcance ANTES de cerrar la base.
     */
    private val viewModels = mutableListOf<MisPedidosViewModel>()

    /** Crea un ViewModel registrado para cancelar su alcance al terminar. */
    private fun nuevoViewModel(): MisPedidosViewModel {
        val model = MisPedidosViewModel()
        viewModels += model
        return model
    }

    @Before
    fun setUp() {
        contexto = ApplicationProvider.getApplicationContext()
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesion = SessionManager(contexto)
        sesion.invalidarSesion()
        sesion.guardarSesion(token = "token-prueba", id = 1L, correo = "prueba@pulgatec.pe")
        servidor = ServidorDePruebas()
        ProveedorDatos.fijar(ContenedorDePruebas.crear(contexto, bd, sesion, servidor))
    }

    @After
    fun tearDown() {
        // Primero se detienen los colectores de Room y después se cierra la
        // base: al revés, Room consultaría una base ya cerrada.
        viewModels.forEach { model -> model.cancelarAlcance() }
        viewModels.clear()
        ProveedorDatos.fijar(null)
        sesion.invalidarSesion()
        bd.close()
        servidor.cerrar()
    }

    /** Espera en tiempo real a que el ViewModel termine (hilo principal). */
    private fun esperar(
        descripcion: String = "la condición esperada",
        condicion: () -> Boolean
    ) = runBlocking {
        esperarCondicion(descripcion) { condicion() }
    }

    private val jsonPedidos = """
        [
          {
            "idPedido": 55,
            "clienteId": 1,
            "fecha": "2024-05-01T10:15:30",
            "total": 120.00,
            "estado": "REGISTRADO",
            "fechaActualizacion": "2024-05-01T10:15:30",
            "detalles": [
              {"idProducto": 900, "nombreProducto": "Teclado", "cantidad": 2,
               "precioUnitario": 60.00, "subtotal": 120.00}
            ],
            "calificacion": null,
            "resenas": [
              {"id": 9, "idPedido": 55, "destino": "PRODUCTO", "productoId": 900,
               "vendedorId": 8, "texto": "Buen producto",
               "fecha": "2024-05-01T11:00:00",
               "fechaModificacion": "2024-05-01T11:00:00"}
            ]
          }
        ]
    """.trimIndent()

    private val jsonResenaActualizada = """
        {"id":9,"idPedido":55,"destino":"PRODUCTO","productoId":900,
         "vendedorId":8,"texto":"Ahora excelente",
         "fecha":"2024-05-01T11:00:00",
         "fechaModificacion":"2024-05-03T08:30:00"}
    """.trimIndent()

    private fun aviso(id: Long, fecha: String) = Mapeadores.aEntidad(
        dto = NotificacionResponse(
            idNotificacion = id,
            tipo = "CAMBIO_ESTADO_PEDIDO",
            resumen = "El pedido 55 cambió de estado",
            fecha = fecha,
            estado = "PENDIENTE",
            idPedido = 55L,
            enlace = "/pedidos/55",
            idConversacion = null,
            fechaLectura = null,
            productoId = null
        ),
        usuarioId = 1L,
        ahora = 1_000L
    )

    // ------------------------------------------------------------ estado inicial
    @Test
    fun elEstadoInicialEsElDeLaPantalla() {
        val model = nuevoViewModel()

        assertTrue(model.pedidos.isEmpty())
        assertTrue(model.pedidosFiltrados.isEmpty())
        assertTrue(model.notificaciones.isEmpty())
        assertFalse(model.cargando)
        assertFalse(model.guardando)
        assertNull(model.error)
        assertNull(model.mensaje)
        assertNull(model.actualizandoEstadoId)
        assertEquals("", model.filtroEstado)
        assertEquals("", model.filtroDesde)
        assertEquals("", model.filtroHasta)
        assertEquals("", model.filtroMontoMin)
        assertEquals("", model.filtroMontoMax)
        assertNull(model.errorFiltro)
        assertEquals(FiltrosPedidos(), model.filtrosAplicados)
    }

    // ------------------------------------------------------------------ HU 46
    @Test
    fun cargarDescargaLasComprasDelUsuario() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()

        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        assertEquals(55L, model.pedidos.first().idPedido)
        assertEquals("Buen producto", model.pedidos.first().resenas?.first()?.texto)
        assertNull(model.error)
        assertFalse(model.cargando)
        assertEquals(listOf("/api/pedidos/me"), servidor.rutasRecibidas())
    }

    @Test
    fun elEstadoDeCargaSeActivaAlDescargar() {
        servidor.servidor.enqueue(
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonPedidos)
                .setHeadersDelay(800, TimeUnit.MILLISECONDS)
        )
        val model = nuevoViewModel()

        model.cargar()

        assertTrue("Debe estar cargando: ${model.cargando}", model.cargando)

        // La escritura del repositorio se refleja de forma asíncrona en el
        // Flow de Room: esperar solo al flag de carga podía leer la lista
        // antes de que llegara la emisión local.
        esperar("fin de carga y emisión de Room") { !model.cargando && model.pedidos.size == 1 }
        assertFalse(model.cargando)
        assertEquals(1, model.pedidos.size)
    }

    @Test
    fun unErrorDeCargaUsaElTextoDelServidor() {
        servidor.responder(400, "{}")
        val model = nuevoViewModel()

        model.cargar()
        esperar { model.error != null }

        assertEquals("Error 400 del servidor.", model.error)
        assertFalse(model.cargando)
        assertTrue(model.pedidos.isEmpty())
    }

    // ------------------------------------------------------------ filtros
    @Test
    fun losFiltrosPorEstadoSeAplicanSobreLasCompras() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        model.filtroEstado = "ENTREGADO"
        model.aplicarFiltros()
        assertNull(model.errorFiltro)
        assertTrue(model.pedidosFiltrados.isEmpty())

        // El estado del pedido se compara sin distinguir mayúsculas.
        model.filtroEstado = "registrado"
        model.aplicarFiltros()
        assertNull(model.errorFiltro)
        assertEquals(1, model.pedidosFiltrados.size)
    }

    @Test
    fun losFiltrosDeFechaYPrecioSeCombinan() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        model.filtroDesde = "2024-05-01"
        model.filtroHasta = "2024-05-01"
        model.filtroMontoMin = "120"
        model.filtroMontoMax = "120"
        model.aplicarFiltros()
        assertNull(model.errorFiltro)
        assertEquals(1, model.pedidosFiltrados.size)

        // Un rango VÁLIDO por encima del total ya no da coincidencias
        // (un rango invertido sería un error de validación, no un filtro).
        model.filtroMontoMin = "121"
        model.filtroMontoMax = "200"
        model.aplicarFiltros()
        assertNull(model.errorFiltro)
        assertTrue(model.pedidosFiltrados.isEmpty())
    }

    @Test
    fun unFiltroInvalidoNoSeAplica() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        model.filtroDesde = "no-es-fecha"
        model.aplicarFiltros()

        assertEquals("Usa fechas reales con formato AAAA-MM-DD.", model.errorFiltro)
        // Los filtros anteriores NO se aplican: la lista sigue completa.
        assertEquals(model.pedidos.size, model.pedidosFiltrados.size)

        model.limpiarFiltros()
        model.filtroMontoMin = "-1"
        model.aplicarFiltros()

        assertEquals("Los montos no pueden ser negativos.", model.errorFiltro)
        assertEquals(model.pedidos.size, model.pedidosFiltrados.size)
    }

    @Test
    fun limpiarFiltrosRestauraElEstadoInicial() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        model.filtroEstado = "CANCELADO"
        model.filtroDesde = "2030-01-01"
        model.aplicarFiltros()
        assertTrue(model.pedidosFiltrados.isEmpty())

        model.limpiarFiltros()

        assertEquals("", model.filtroEstado)
        assertEquals("", model.filtroDesde)
        assertEquals("", model.filtroHasta)
        assertEquals("", model.filtroMontoMin)
        assertEquals("", model.filtroMontoMax)
        assertNull(model.errorFiltro)
        assertEquals(FiltrosPedidos(), model.filtrosAplicados)
        assertEquals(model.pedidos.size, model.pedidosFiltrados.size)
    }

    // ------------------------------------------------------- avisos (Room)
    @Test
    fun losAvisosSeObservanDesdeElRepositorio() {
        val model = nuevoViewModel()

        runBlocking { bd.notificacionDao().guardarTodas(listOf(aviso(id = 10, fecha = "2024-05-01T10:15:30"))) }
        esperar { model.notificaciones.isNotEmpty() }

        val aviso = model.notificaciones.first()
        assertEquals(10L, aviso.idNotificacion)
        assertEquals("CAMBIO_ESTADO_PEDIDO", aviso.tipo)
        assertEquals("El pedido 55 cambió de estado", aviso.resumen)
        // La copia local guarda las fechas normalizadas al formato fijo.
        assertEquals("2024-05-01T10:15:30.000000000", aviso.fecha)
        assertEquals(55L, aviso.idPedido)
    }

    @Test
    fun losAvisosSeActualizanCuandoRoomCambia() {
        val model = nuevoViewModel()

        runBlocking { bd.notificacionDao().guardarTodas(listOf(aviso(id = 10, fecha = "2024-05-01T10:00:00"))) }
        esperar { model.notificaciones.size == 1 }

        runBlocking { bd.notificacionDao().guardarTodas(listOf(aviso(id = 11, fecha = "2024-05-01T11:00:00"))) }
        esperar { model.notificaciones.size == 2 }

        // El orden del DAO (fecha DESC) se conserva en el ViewModel.
        assertEquals(listOf(11L, 10L), model.notificaciones.map { it.idNotificacion })
    }

    // ------------------------------------------------------- HU 45 - reseñas
    @Test
    fun editarResenaGuardaElTextoNuevoEnRoom() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        servidor.responder(200, jsonResenaActualizada)
        model.editarResena(idPedido = 55, idResena = 9, texto = "   Ahora excelente   ")
        esperar { model.mensaje != null }

        assertEquals("Reseña actualizada correctamente.", model.mensaje)
        esperar { model.pedidos.first().resenas?.firstOrNull()?.texto == "Ahora excelente" }
        assertEquals("Ahora excelente", model.pedidos.first().resenas?.first()?.texto)
        assertFalse(model.guardando)

        // El texto viaja RECORTADO al backend (PUT /api/resenas/9).
        val peticion = servidor.peticiones().first { it.method == "PUT" }
        assertEquals("/api/resenas/9", peticion.path)
        val cuerpo = peticion.body.readUtf8()
        assertTrue("Debe enviar el texto: $cuerpo", cuerpo.contains("\"texto\":\"Ahora excelente\""))
    }

    @Test
    fun elErrorDeEdicionUsaElPrimerMensaje() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        servidor.responderError(400, "La reseña no existe")
        model.editarResena(idPedido = 55, idResena = 9, texto = "Nuevo")
        esperar { model.mensaje != null }

        assertEquals("La reseña no existe", model.mensaje)
        assertFalse(model.guardando)
        // La reseña anterior se conserva.
        assertEquals("Buen producto", model.pedidos.first().resenas?.first()?.texto)
    }

    @Test
    fun elErrorDeEdicionSinMessagesUsaElTextoPorCodigo() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        servidor.responder(400, "{}")
        model.editarResena(idPedido = 55, idResena = 9, texto = "Nuevo")
        esperar { model.mensaje != null }

        assertEquals("Error 400 al consultar pedidos.", model.mensaje)
        assertEquals("Buen producto", model.pedidos.first().resenas?.first()?.texto)
    }

    @Test
    fun sinConexionLaEdicionInformaElErrorSinPrefijos() {
        servidor.responder(200, jsonPedidos)
        val model = nuevoViewModel()
        model.cargar()
        esperar { model.pedidos.isNotEmpty() }

        servidor.apagar()
        model.editarResena(idPedido = 55, idResena = 9, texto = "Nuevo")
        esperar { model.mensaje != null }

        val mensaje = model.mensaje!!
        assertTrue("Mensaje crudo: $mensaje", mensaje.isNotBlank())
        assertFalse(mensaje.startsWith("Error "))
        assertFalse(mensaje.startsWith("No hay conexión"))
        assertFalse(mensaje.startsWith("Reseña actualizada"))
        assertEquals("Buen producto", model.pedidos.first().resenas?.first()?.texto)
        assertFalse(model.guardando)
    }

    @Test
    fun unaResenaEnBlancoOLargaNoLlamaAlBackend() {
        val model = nuevoViewModel()

        model.editarResena(idPedido = 55, idResena = 9, texto = "    ")
        model.editarResena(idPedido = 55, idResena = 9, texto = "")
        model.editarResena(idPedido = 55, idResena = 9, texto = "x".repeat(1001))

        assertNull(model.mensaje)
        assertFalse(model.guardando)
        assertEquals("No debe llamar al backend", 0, servidor.peticionesRecibidas())
    }

    // -------------------------------------------------------- HU 47 - estado
    @Test
    fun actualizarEstadoConfirmaElEstadoConsultado() {
        servidor.responder(
            200,
            """{"idPedido":55,"estado":"EN_PROCESO","fechaActualizacion":"2024-05-02T10:00:00"}"""
        )
        val model = nuevoViewModel()

        model.actualizarEstado(55)
        esperar { model.mensaje != null }

        assertEquals("Estado del pedido actualizado.", model.mensaje)
        assertNull(model.actualizandoEstadoId)
        assertNull(model.error)
        assertEquals(listOf("/api/pedidos/55/estado"), servidor.rutasRecibidas())
    }

    @Test
    fun unErrorDeEstadoTemporalActivaElModoSinConexion() {
        servidor.responder(500, "{}")
        val model = nuevoViewModel()

        model.actualizarEstado(55)
        esperar { model.mensaje != null }

        assertEquals("Error 500 del servidor.", model.mensaje)
        assertTrue("Debe avisar que no hay red: ${model.sinConexion}", model.sinConexion)
        assertNull(model.actualizandoEstadoId)
    }
}
