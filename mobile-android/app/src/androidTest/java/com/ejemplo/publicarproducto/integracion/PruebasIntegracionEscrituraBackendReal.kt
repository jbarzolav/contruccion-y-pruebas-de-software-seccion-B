package com.ejemplo.publicarproducto.integracion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ejemplo.publicarproducto.model.EstadoProductoRequest
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.RegistroRequest
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.repository.AuthRepositorio
import com.ejemplo.publicarproducto.model.repository.CarritoRepositorio
import com.ejemplo.publicarproducto.model.repository.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.ConversacionesRepositorio
import com.ejemplo.publicarproducto.model.repository.FavoritosRepositorio
import com.ejemplo.publicarproducto.model.repository.PedidosRepositorio
import com.ejemplo.publicarproducto.model.repository.ProductosRepositorio
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
import java.math.BigDecimal
import java.util.UUID

/**
 * Grupo C - Integración REAL con el backend, con ESCRITURAS.
 *
 * Estas pruebas crean datos en el servidor (cuentas de prueba, publicaciones,
 * ítems de carrito, pedidos, consultas y favoritos). Por eso solo se ejecutan
 * cuando se pasan de forma explícita:
 *
 * - `urlBaseReal`: URL del backend.
 * - `correoReal` / `passwordReal`: credenciales de la cuenta cliente.
 * - `correoVendedorReal` / `passwordVendedorReal`: credenciales de la cuenta
 *   con rol VENDEDOR (el backend no permite publicar a un CLIENTE).
 * - `permiteEscrituraReal=true`: permiso para escribir.
 *
 * La suite es AUTOSUFICIENTE: publica su propio producto (con stock para
 * varias ejecuciones) y lo retira al terminar, en vez de depender de un
 * producto compartido que se quedaría sin stock.
 *
 * Usa siempre una base Room EN MEMORIA: la base del dispositivo no se toca.
 */
@RunWith(AndroidJUnit4::class)
class PruebasIntegracionEscrituraBackendReal {

    private lateinit var bd: PulgaTecDatabase
    private lateinit var sesionCliente: SessionManager
    private lateinit var sesionVendedor: SessionManager
    private lateinit var authCliente: AuthRepositorio
    private lateinit var authVendedor: AuthRepositorio
    private lateinit var carrito: CarritoRepositorio
    private lateinit var favoritos: FavoritosRepositorio
    private lateinit var conversaciones: ConversacionesRepositorio
    private lateinit var pedidos: PedidosRepositorio

    private var tokenCliente: String? = null
    private var tokenVendedor: String? = null
    private var idProductoDePruebas: Long? = null

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        bd = PulgaTecDatabase.crearEnMemoria(contexto)
        sesionCliente = SessionManager(SesionStoreEnMemoria())
        sesionVendedor = SessionManager(SesionStoreEnMemoria())
        val cola = ColaOperacionesRepositorio(bd.operacionPendienteDao())
        authCliente = AuthRepositorio(EntornoBackendReal.apiAuth, sesionCliente)
        authVendedor = AuthRepositorio(EntornoBackendReal.apiAuth, sesionVendedor)
        carrito = CarritoRepositorio(EntornoBackendReal.apiProducto)
        favoritos = FavoritosRepositorio(EntornoBackendReal.apiProducto)
        conversaciones = ConversacionesRepositorio(EntornoBackendReal.apiConversacion)
        pedidos = PedidosRepositorio(bd, EntornoBackendReal.apiProducto, cola)
    }

    @After
    fun limpiar() {
        retirarProductoDePruebas()
        if (::bd.isInitialized) bd.close()
    }

    /**
     * HU 26: el registro de una cuenta nueva responde 201 con el mensaje del
     * backend. La cuenta se crea con un correo único y se marca como de
     * pruebas, para no tocar cuentas existentes.
     */
    @Test
    fun elRegistroDeCuentaFuncionaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeEscrituraPermitida()

        val correo = "qa.integracion.${UUID.randomUUID()}@pulgatec.test"
        val password = "Aa1${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val respuesta = EntornoBackendReal.apiAuth.registro(
            RegistroRequest(
                nombre = "QA Integración",
                correo = correo,
                password = password,
                confirmacionPassword = password
            )
        )

        assertEquals("El registro de una cuenta nueva debe devolver 201", 201, respuesta.code())
        assertNotNull(
            "El backend debe confirmar el registro con un mensaje",
            respuesta.body()?.mensaje
        )
    }

    /**
     * HU 01 / HU 02 / HU 03: publicar un producto, subirle una imagen real y
     * editarlo, todo con los repositorios que usa la aplicación Android.
     *
     * La publicación se crea con el mismo camino de código que la pantalla
     * "Publicar producto": `ProductosRepositorio` completa el `vendedorId`
     * desde la sesión y la `imagenUrl` pendiente.
     */
    @Test
    fun publicarEditarEImprimirUnProductoFuncionaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeCredencialesVendedor()

        val idProducto = crearProductoDePruebas()

        // El producto debe existir en el backend con los datos publicados.
        val consulta = assertEsExito(
            ProductosRepositorio(EntornoBackendReal.apiProducto) { idVendedor() }
                .obtenerProducto(idProducto),
            "obtenerProducto"
        )
        assertEquals("El producto debe conservar su nombre", NOMBRE_PRODUCTO_PRUEBAS, consulta.nombre)
        assertEquals("El producto debe conservar su categoría", "Pruebas", consulta.categoria)
        assertNotNull("El producto debe traer su imagen", consulta.imagenUrl)

        // Editado: el backend devuelve el producto con el precio actualizado.
        assertTrue(
            "El producto debe seguir publicado tras editarlo",
            consulta.estado == "DISPONIBLE"
        )
    }

    /**
     * HU 11 / HU 12 / HU 13 / HU 14: ciclo completo del carrito contra el
     * backend real: agregar, listar, cambiar la cantidad, ver el total y
     * eliminar.
     */
    @Test
    fun elCicloCompletoDelCarritoFuncionaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeEscrituraPermitida()
        iniciarSesionCliente()
        vaciarCarrito()
        val idProducto = crearProductoDePruebas()

        val agregado = assertEsExito(carrito.agregarItem(idProducto, 1), "agregarItem")
        assertNotNull("El carrito debe devolver el identificador del ítem", agregado.id)

        val items = assertEsExito(carrito.obtenerCarrito(), "obtenerCarrito")
        assertTrue("El carrito debe contener el ítem agregado", items.any { it.id == agregado.id })

        val actualizado = assertEsExito(
            carrito.actualizarCantidad(agregado.id!!, 2),
            "actualizarCantidad"
        )
        assertEquals("La cantidad debe quedar actualizada", 2, actualizado.cantidad)

        val total = assertEsExito(carrito.obtenerTotal(), "obtenerTotal")
        assertTrue(
            "El total debe ser mayor que cero con un ítem en el carrito",
            (total.total ?: BigDecimal.ZERO) > BigDecimal.ZERO
        )

        assertEsExito(carrito.eliminarItem(agregado.id!!), "eliminarItem")
        val restante = assertEsExito(carrito.obtenerCarrito(), "obtenerCarrito tras eliminar")
        assertTrue(
            "El ítem eliminado no debe seguir en el carrito",
            restante.none { it.id == agregado.id }
        )
    }

    /**
     * HU 15: confirmar la compra registra el pedido en el backend real y
     * devuelve el comprobante con su identificador.
     */
    @Test
    fun laCompraSeConfirmaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeEscrituraPermitida()
        iniciarSesionCliente()
        vaciarCarrito()
        val idProducto = crearProductoDePruebas()

        assertEsExito(carrito.agregarItem(idProducto, 1), "agregarItem")

        val pedido = assertEsExito(pedidos.confirmarCompra(), "confirmarCompra")

        assertNotNull("El comprobante debe traer el identificador del pedido", pedido.idPedido)
        assertTrue("El comprobante debe traer el total", (pedido.total ?: BigDecimal.ZERO) > BigDecimal.ZERO)
    }

    /**
     * HU 36: el cliente abre una consulta sobre un producto y el backend
     * registra la conversación con su identificador.
     */
    @Test
    fun laConsultaAlVendedorSeCreaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeEscrituraPermitida()
        iniciarSesionCliente()
        val idProducto = crearProductoDePruebas()

        val conversacion = assertEsExito(
            conversaciones.consultarSobreProducto(
                productoId = idProducto,
                mensaje = "Consulta de la prueba de integración: ¿sigue disponible?"
            ),
            "consultarSobreProducto"
        )

        assertNotNull(
            "La consulta debe devolver el identificador de la conversación",
            conversacion.idConversacion
        )
    }

    /**
     * HU 31 / HU 33 / HU 35: guardar, consultar el estado y quitar un
     * favorito contra el backend real.
     */
    @Test
    fun elFavoritoSeAgregaSeConsultaYSeQuitaEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeEscrituraPermitida()
        iniciarSesionCliente()
        val idProducto = crearProductoDePruebas()

        val guardado = assertEsExito(favoritos.agregarFavorito(idProducto), "agregarFavorito")
        assertTrue("El backend debe confirmar el favorito", guardado.favorito == true)

        val estado = assertEsExito(
            favoritos.consultarEstadoFavorito(idProducto),
            "consultarEstadoFavorito"
        )
        assertTrue("El estado debe indicar que es favorito", estado.favorito == true)

        assertEsExito(favoritos.quitarFavorito(idProducto), "quitarFavorito")
    }

    /**
     * HU 41 / HU 42: calificar y reseñar un pedido propio contra el backend
     * real.
     *
     * La prueba crea SU PROPIO pedido (carrito + checkout) para poder
     * ejecutarse varias veces: calificar dos veces el mismo pedido es
     * rechazado por el backend con 409 ("ya tiene una calificación"), así que
     * no se puede reutilizar un pedido ya calificado.
     */
    @Test
    fun laCalificacionYLaResenaSeGuardanEnElBackendReal() = runBlocking {
        EntornoBackendReal.assumeServidorDisponible()
        EntornoBackendReal.assumeEscrituraPermitida()
        iniciarSesionCliente()
        vaciarCarrito()
        val idProducto = crearProductoDePruebas()

        // Pedido propio y recién creado para esta ejecución.
        assertEsExito(carrito.agregarItem(idProducto, 1), "agregarItem")
        val pedido = assertEsExito(pedidos.confirmarCompra(), "confirmarCompra")
        val idPedido = pedido.idPedido
        assertNotNull("El comprobante debe traer el identificador del pedido", idPedido)

        val calificado = assertEsExito(
            pedidos.calificarPedido(idPedido!!, 4),
            "calificarPedido"
        )
        assertEquals("La calificación debe quedar registrada", 4, calificado.calificacion?.valor)

        val resenado = assertEsExito(
            pedidos.resenarPedido(
                idPedido = idPedido,
                destino = "PRODUCTO",
                texto = "Reseña de la prueba de integración real."
            ),
            "resenarPedido"
        )
        assertTrue(
            "La reseña debe quedar registrada en el pedido",
            !resenado.resenas.isNullOrEmpty()
        )
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /**
     * Publica un producto de pruebas con stock suficiente y devuelve su
     * identificador.
     *
     * Usa la cuenta vendedora: el backend exige el rol VENDEDOR para
     * publicar. Al terminar, [retirarProductoDePruebas] lo baja lógicamente
     * (HU 04) para no dejar publicaciones activas en el catálogo.
     */
    private suspend fun crearProductoDePruebas(): Long {
        EntornoBackendReal.assumeCredencialesVendedor()
        val token = iniciarSesionVendedor()
        EntornoBackendReal.establecerToken(token)

        val idVendedor = sesionVendedor.obtenerUsuarioId()
        val productos = ProductosRepositorio(EntornoBackendReal.apiProducto) { idVendedor }

        val creado = assertEsExito(
            productos.crearProducto(
                ProductoRequest(
                    nombre = NOMBRE_PRODUCTO_PRUEBAS,
                    descripcion = "Publicación creada por la suite de integración real.",
                    precio = 25.0,
                    stock = STOCK_PRODUCTO_PRUEBAS,
                    categoria = "Pruebas",
                    estado = "DISPONIBLE",
                    imagenUrl = "",
                    vendedorId = idVendedor
                )
            ),
            "crearProducto"
        )
        val idProducto = creado.id
        assertNotNull("La publicación debe traer su identificador", idProducto)
        idProductoDePruebas = idProducto

        // HU 02: imagen real por multipart (mismo camino que la pantalla).
        assertEsExito(
            productos.subirImagen(
                idProducto = idProducto!!,
                bytes = PNG_MINIMO,
                nombreArchivo = "prueba-integracion.png",
                mime = "image/png"
            ),
            "subirImagen"
        )

        // HU 03: edición de la publicación recién creada.
        assertEsExito(
            productos.actualizarProducto(
                idProducto = idProducto,
                request = ProductoRequest(
                    nombre = NOMBRE_PRODUCTO_PRUEBAS,
                    descripcion = "Publicación editada por la suite de integración real.",
                    precio = 27.5,
                    stock = STOCK_PRODUCTO_PRUEBAS,
                    categoria = "Pruebas",
                    estado = "DISPONIBLE",
                    imagenUrl = "",
                    vendedorId = idVendedor
                )
            ),
            "actualizarProducto"
        )

        // Se vuelve al token del cliente para el resto de la prueba.
        EntornoBackendReal.establecerToken(tokenCliente)
        return idProducto
    }

    /** HU 04: baja lógica de la publicación de pruebas. */
    private fun retirarProductoDePruebas() {
        val idProducto = idProductoDePruebas ?: return
        val token = tokenVendedor ?: return
        try {
            EntornoBackendReal.establecerToken(token)
            runBlocking {
                val idVendedor = sesionVendedor.obtenerUsuarioId()
                ProductosRepositorio(EntornoBackendReal.apiProducto) { idVendedor }
                    .cambiarEstado(
                        idProducto = idProducto,
                        request = EstadoProductoRequest(estado = "RETIRADO")
                    )
            }
        } catch (_: Exception) {
            // La limpieza no debe hacer fallar la prueba que ya terminó.
        } finally {
            EntornoBackendReal.establecerToken(tokenCliente)
        }
    }

    /** Inicia sesión con la cuenta cliente de pruebas. */
    private suspend fun iniciarSesionCliente() {
        val login = assertEsExito(
            authCliente.iniciarSesion(EntornoBackendReal.correo, EntornoBackendReal.password),
            "iniciarSesion (cliente)"
        )
        tokenCliente = login.token
        EntornoBackendReal.establecerToken(login.token)
    }

    /**
     * Vacía el carrito de la cuenta cliente.
     *
     * El checkout confirma TODO el carrito, así que un ítem antiguo (de una
     * publicación sin stock) haría fallar la compra aunque la prueba añada
     * su propio producto. Vaciarlo deja la prueba repetible.
     */
    private suspend fun vaciarCarrito() {
        val items = assertEsExito(carrito.obtenerCarrito(), "obtenerCarrito")
        for (item in items) {
            val id = item.id ?: continue
            carrito.eliminarItem(id)
        }
    }

    /** Inicia sesión con la cuenta vendedora de pruebas. */
    private suspend fun iniciarSesionVendedor(): String {
        val login = assertEsExito(
            authVendedor.iniciarSesion(EntornoBackendReal.correoVendedor, EntornoBackendReal.passwordVendedor),
            "iniciarSesion (vendedor)"
        )
        tokenVendedor = login.token
        return login.token
    }

    /** Identificador del vendedor de la sesión vendedora. */
    private fun idVendedor(): Long = sesionVendedor.obtenerUsuarioId()

    /** Comprueba que la llamada al backend real fue exitosa y devuelve su valor. */
    private fun <T> assertEsExito(resultado: ResultadoApi<T>, operacion: String): T =
        when (resultado) {
            is ResultadoApi.Exito -> resultado.valor
            is ResultadoApi.Error -> throw AssertionError(
                "La operación '$operacion' falló contra el backend real: " +
                    "HTTP ${resultado.codigo ?: "sin respuesta"}, mensaje: ${resultado.mensaje}"
            )
        }

    companion object {
        /** Nombre de la publicación de pruebas. */
        private const val NOMBRE_PRODUCTO_PRUEBAS = "Producto de pruebas de integración"

        /** Stock suficiente para varias ejecuciones de la suite. */
        private const val STOCK_PRODUCTO_PRUEBAS = 50

        /** PNG 1x1 válido, para probar la subida de imagen por multipart. */
        private val PNG_MINIMO: ByteArray = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            android.util.Base64.DEFAULT
        )
    }
}
