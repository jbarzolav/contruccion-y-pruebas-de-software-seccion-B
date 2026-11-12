package com.ejemplo.publicarproducto.integracion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.repository.AuthRepositorio
import com.ejemplo.publicarproducto.model.repository.CarritoRepositorio
import com.ejemplo.publicarproducto.model.repository.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.ConversacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.FavoritosRepositorio
import com.ejemplo.publicarproducto.model.repository.NotificacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.PedidosRepositorio
import com.ejemplo.publicarproducto.model.session.SesionStoreEnMemoria
import com.ejemplo.publicarproducto.model.session.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grupo C - Integración REAL con sesión autenticada (solo lectura).
 *
 * Comprueba que la aplicación procesa correctamente las respuestas del
 * backend real cuando la petición lleva la cabecera `Authorization: Bearer`.
 *
 * Usa los repositorios reales (los mismos que consume la aplicación) sobre
 * una base Room EN MEMORIA: la base de datos del dispositivo no se toca.
 *
 * No crea ni modifica datos en el servidor.
 */
@RunWith(AndroidJUnit4::class)
class PruebasIntegracionSesionBackendReal {

    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesion: SessionManager
    private lateinit var auth: AuthRepositorio
    private lateinit var carrito: CarritoRepositorio
    private lateinit var favoritos: FavoritosRepositorio
    private lateinit var conversaciones: ConversacionesRepositorio
    private lateinit var pedidos: PedidosRepositorio
    private lateinit var notificaciones: NotificacionesRepositorio

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesion = SessionManager(SesionStoreEnMemoria())
        val cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        auth = AuthRepositorio(EntornoBackendReal.apiAuth, sesion)
        carrito = CarritoRepositorio(EntornoBackendReal.apiProducto)
        favoritos = FavoritosRepositorio(EntornoBackendReal.apiProducto)
        conversaciones = ConversacionesRepositorio(EntornoBackendReal.apiConversacion)
        pedidos = PedidosRepositorio(bd, EntornoBackendReal.apiProducto, cola)
        notificaciones = NotificacionesRepositorio(bd, EntornoBackendReal.apiProducto, cola)
    }

    @After
    fun limpiar() {
        if (::bd.isInitialized) bd.close()
    }

    @Test
    fun elLoginSeRealizaContraElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()

        val login = assertEsExito(
            auth.iniciarSesion(EntornoBackendReal.correo, EntornoBackendReal.password),
            "iniciarSesion"
        )

        assertTrue("El login debe devolver un token", !login.token.isNullOrBlank())
        assertTrue("El login debe devolver el identificador del usuario", login.id > 0)
        assertTrue("El backend debe devolver el rol", !login.rol.isNullOrBlank())
    }

    @Test
    fun laSesionRegistradaPermiteConsultarElPerfilEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()
        iniciarSesion()

        val perfil = assertEsExito(auth.obtenerPerfil(), "obtenerPerfil")

        assertNotNull("El perfil debe traer el identificador del usuario", perfil.id)
        assertEquals(
            "El perfil debe corresponder al usuario de la sesión",
            sesion.obtenerUsuarioId(),
            perfil.id
        )
    }

    @Test
    fun elCarritoSeConsultaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()
        iniciarSesion()

        assertEsExito(carrito.obtenerCarrito(), "obtenerCarrito")
        val total = assertEsExito(carrito.obtenerTotal(), "obtenerTotal")
        assertNotNull("El total del carrito debe venir del backend real", total.total)
    }

    @Test
    fun losFavoritosSeConsultanEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()
        iniciarSesion()

        val favoritos = assertEsExito(favoritos.listarFavoritos(), "listarFavoritos")

        assertNotNull("Los favoritos deben venir del backend real", favoritos)
    }

    @Test
    fun laConfiguracionDeConsultasVieneDelBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()
        iniciarSesion()

        val configuracion = assertEsExito(conversaciones.configuracion(), "configuracion")

        assertTrue(
            "El backend debe indicar la longitud máxima de un mensaje",
            configuracion.longitudMaxima > 0
        )
    }

    @Test
    fun lasConversacionesSeConsultanEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()
        iniciarSesion()

        val conversaciones =
            assertEsExito(conversaciones.listarConversaciones(), "listarConversaciones")

        assertNotNull("Las conversaciones deben venir del backend real", conversaciones)
    }

    @Test
    fun lasComprasSeConsultanEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()
        iniciarSesion()

        val compras = assertEsExito(
            pedidos.refrescarCompras(sesion.obtenerUsuarioId()),
            "refrescarCompras"
        )

        assertNotNull("Las compras deben venir del backend real", compras)
    }

    @Test
    fun laBandejaDeNotificacionesSeConsultaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()
        iniciarSesion()

        val bandeja = assertEsExito(
            notificaciones.refrescar(
                usuarioId = sesion.obtenerUsuarioId(),
                tipo = null,
                estado = null,
                desde = null,
                hasta = null,
                pagina = 0
            ),
            "refrescar bandeja"
        )

        assertNotNull("La bandeja debe traer la lista de notificaciones", bandeja.notificaciones)
    }

    /**
     * HU 47: el estado de un pedido propio se consulta contra el backend real
     * y debe corresponder al pedido pedido.
     */
    @Test
    fun elEstadoDeUnPedidoVieneDelBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredenciales()
        val idPedido = EntornoBackendReal.idPedido
        EntornoBackendReal.assumeId(EntornoBackendReal.CLAVE_ID_PEDIDO, idPedido)
        iniciarSesion()

        val estado = assertEsExito(
            pedidos.refrescarEstadoPedido(sesion.obtenerUsuarioId(), idPedido!!),
            "refrescarEstadoPedido"
        )

        assertEquals(
            "El estado debe corresponder al pedido consultado",
            idPedido,
            estado.idPedido
        )
        assertTrue(
            "El backend debe devolver el estado actual del pedido",
            !estado.estado.isNullOrBlank()
        )
    }

    /**
     * Un token inválido tiene que ser rechazado con 401 por el backend real:
     * es lo que permite a la app cerrar la sesión cuando vence.
     */
    @Test
    fun unTokenInvalidoDevuelve401EnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()

        EntornoBackendReal.establecerToken("token-inexistente-de-prueba")
        try {
            val respuesta = EntornoBackendReal.apiAuth.obtenerSesion()
            assertEquals("Un token inválido debe devolver 401", 401, respuesta.code())
        } finally {
            EntornoBackendReal.establecerToken(null)
        }
    }

    /** Inicia sesión con la cuenta de pruebas y publica el token en el entorno. */
    private suspend fun iniciarSesion() {
        val login = assertEsExito(
            auth.iniciarSesion(EntornoBackendReal.correo, EntornoBackendReal.password),
            "iniciarSesion"
        )
        EntornoBackendReal.establecerToken(login.token)
    }

    /** Comprueba que la llamada al backend real fue exitosa y devuelve su valor. */
    private fun <T> assertEsExito(resultado: ResultadoApi<T>, operacion: String): T =
        when (resultado) {
            is ResultadoApi.Exito -> resultado.valor
            is ResultadoApi.Error -> throw AssertionError(
                "La operación '$operacion' falló contra el backend real: " +
                    "HTTP ${resultado.codigo ?: "sin respuesta"}, mensaje: ${resultado.mensaje}"
            )
        }
}
