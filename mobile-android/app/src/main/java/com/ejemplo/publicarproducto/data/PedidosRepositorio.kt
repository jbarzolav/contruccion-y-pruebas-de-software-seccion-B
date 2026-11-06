package com.ejemplo.publicarproducto.data

import androidx.room.withTransaction
import com.ejemplo.publicarproducto.local.Ambitos
import com.ejemplo.publicarproducto.local.EntidadOperacionPendiente
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.local.TiposOperacion
import com.ejemplo.publicarproducto.model.EstadoPedidoResponse
import com.ejemplo.publicarproducto.model.PedidoDetalleResponse
import com.ejemplo.publicarproducto.model.PedidoResponse
import com.ejemplo.publicarproducto.model.PedidoVendedorResponse
import com.ejemplo.publicarproducto.model.ResenaResponse
import com.ejemplo.publicarproducto.network.ProductoApi
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.network.ejecutarPeticion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Pedidos del usuario (compras HU 46/50 y ventas HU 48/49) con lectura
 * Offline First.
 *
 * La interfaz consume la copia local de Room; con internet cada refresco
 * guarda en Room la respuesta real del backend y la interfaz se actualiza
 * sola. Sin internet se muestran los últimos datos sincronizados.
 */
class PedidosRepositorio(
    private val bd: PulgaTecDatabase,
    private val api: ProductoApi,
    private val cola: ColaOperacionesRepositorio
) {

    /** Compras del usuario observadas desde la copia local. */
    fun observarCompras(usuarioId: Long): Flow<List<PedidoResponse>> = combine(
        bd.pedidoDao().observarPedidos(usuarioId, Ambitos.COMPRAS),
        bd.pedidoDao().observarDetallesUsuario(usuarioId),
        bd.pedidoDao().observarResenasUsuario(usuarioId),
        bd.pedidoDao().observarCalificacionesUsuario(usuarioId)
    ) { pedidos, detalles, resenas, calificaciones ->
        pedidos.map { pedido ->
            Mapeadores.aDto(
                pedido = pedido,
                detalles = detalles.filter { it.idPedido == pedido.idPedido && it.ambito == Ambitos.COMPRAS },
                resenas = resenas.filter { it.idPedido == pedido.idPedido },
                calificacion = calificaciones.firstOrNull { it.idPedido == pedido.idPedido }
            )
        }
    }

    /** Ventas del usuario observadas desde la copia local. */
    fun observarVentas(usuarioId: Long): Flow<VentasLocales> = combine(
        bd.pedidoDao().observarPedidos(usuarioId, Ambitos.VENTAS),
        bd.pedidoDao().observarDetallesUsuario(usuarioId)
    ) { pedidos, detalles ->
        VentasLocales(
            pedidos = pedidos.map { pedido ->
                Mapeadores.aDtoPedidoVendedor(
                    pedido,
                    detalles.filter { it.idPedido == pedido.idPedido && it.ambito == Ambitos.VENTAS }
                )
            },
            estadosPendientes = pedidos
                .mapNotNull { pedido -> pedido.estadoPendienteLocal?.let { pedido.idPedido to it } }
                .toMap()
        )
    }

    /** Detalle de un pedido propio observado desde la copia local. */
    fun observarDetalle(usuarioId: Long, idPedido: Long): Flow<PedidoDetalleResponse?> = combine(
        bd.pedidoDao().observarPedido(usuarioId, idPedido, Ambitos.COMPRAS),
        bd.pedidoDao().observarDetalles(usuarioId, idPedido, Ambitos.COMPRAS)
    ) { pedido, detalles ->
        pedido?.let { Mapeadores.aDtoPedidoDetalle(it, detalles) }
    }

    /** Estado propuesto por el usuario que el servidor todavía no validó. */
    fun observarEstadoPendiente(usuarioId: Long, idPedido: Long): Flow<String?> =
        bd.pedidoDao().observarPedido(usuarioId, idPedido, Ambitos.VENTAS).map { it?.estadoPendienteLocal }

    /** Descarga los pedidos propios reales del backend y actualiza Room. */
    suspend fun refrescarCompras(usuarioId: Long): ResultadoApi<List<PedidoResponse>> {
        val resultado = ejecutarPeticion { api.listarMisPedidos() }
        if (resultado is ResultadoApi.Exito) {
            val ahora = System.currentTimeMillis()
            bd.withTransaction {
                val pendientes = bd.pedidoDao()
                    .listarPedidos(usuarioId, Ambitos.COMPRAS)
                    .mapNotNull { pedido -> pedido.estadoPendienteLocal?.let { pedido.idPedido to it } }
                    .toMap()
                val pedidos = resultado.valor.mapNotNull { pedido ->
                    Mapeadores.aEntidad(
                        dto = pedido,
                        usuarioId = usuarioId,
                        ambito = Ambitos.COMPRAS,
                        ahora = ahora,
                        estadoPendienteLocal = pendientes[pedido.idPedido]
                    )
                }
                bd.pedidoDao().guardarPedidos(pedidos)
                val productos = mutableListOf<com.ejemplo.publicarproducto.local.EntidadProducto>()
                resultado.valor.forEach { pedido ->
                    val idPedido = pedido.idPedido ?: return@forEach
                    bd.pedidoDao().borrarDetalles(usuarioId, idPedido, Ambitos.COMPRAS)
                    bd.pedidoDao().insertarDetalles(
                        Mapeadores.aEntidadesDetalle(
                            detalles = pedido.detalles,
                            usuarioId = usuarioId,
                            idPedido = idPedido,
                            ambito = Ambitos.COMPRAS,
                            ahora = ahora
                        )
                    )
                    bd.pedidoDao().borrarResenas(usuarioId, idPedido)
                    bd.pedidoDao().guardarResenas(
                        Mapeadores.aEntidadesResena(pedido.resenas, usuarioId, idPedido)
                    )
                    bd.pedidoDao().borrarCalificaciones(usuarioId, idPedido)
                    bd.pedidoDao().guardarCalificaciones(
                        Mapeadores.aEntidadesCalificacion(pedido.calificacion, usuarioId, idPedido)
                    )
                    productos += Mapeadores.aEntidadesProducto(pedido.detalles, usuarioId, ahora)
                }
                if (productos.isNotEmpty()) bd.productoDao().guardar(productos)
            }
        }
        return resultado
    }

    /** Descarga los pedidos recibidos reales del backend y actualiza Room. */
    suspend fun refrescarVentas(usuarioId: Long): ResultadoApi<List<PedidoVendedorResponse>> {
        val resultado = ejecutarPeticion { api.listarPedidosRecibidos() }
        if (resultado is ResultadoApi.Exito) {
            val ahora = System.currentTimeMillis()
            bd.withTransaction {
                val pendientes = bd.pedidoDao()
                    .listarPedidos(usuarioId, Ambitos.VENTAS)
                    .mapNotNull { pedido -> pedido.estadoPendienteLocal?.let { pedido.idPedido to it } }
                    .toMap()
                val pedidos = resultado.valor.map { pedido ->
                    Mapeadores.aEntidad(
                        dto = pedido,
                        usuarioId = usuarioId,
                        ahora = ahora,
                        estadoPendienteLocal = pendientes[pedido.idPedido]
                    )
                }
                bd.pedidoDao().guardarPedidos(pedidos)
                val productos = mutableListOf<com.ejemplo.publicarproducto.local.EntidadProducto>()
                resultado.valor.forEach { pedido ->
                    bd.pedidoDao().borrarDetalles(usuarioId, pedido.idPedido, Ambitos.VENTAS)
                    bd.pedidoDao().insertarDetalles(
                        Mapeadores.aEntidadesDetalle(
                            detalles = pedido.productos,
                            usuarioId = usuarioId,
                            idPedido = pedido.idPedido,
                            ambito = Ambitos.VENTAS,
                            ahora = ahora
                        )
                    )
                    productos += Mapeadores.aEntidadesProducto(pedido.productos, usuarioId, ahora)
                }
                if (productos.isNotEmpty()) bd.productoDao().guardar(productos)
            }
        }
        return resultado
    }

    /** Descarga el detalle real del pedido y actualiza Room. */
    suspend fun refrescarDetalle(usuarioId: Long, idPedido: Long): ResultadoApi<PedidoDetalleResponse> {
        val resultado = ejecutarPeticion { api.obtenerDetallePedido(idPedido) }
        if (resultado is ResultadoApi.Exito) {
            val ahora = System.currentTimeMillis()
            bd.withTransaction {
                val pendiente = bd.pedidoDao()
                    .buscarPedido(usuarioId, idPedido, Ambitos.COMPRAS)
                    ?.estadoPendienteLocal
                val pedido = Mapeadores.aEntidad(
                    dto = resultado.valor,
                    usuarioId = usuarioId,
                    ahora = ahora,
                    estadoPendienteLocal = pendiente
                )
                if (pedido != null) bd.pedidoDao().guardarPedidos(listOf(pedido))
                bd.pedidoDao().borrarDetalles(usuarioId, idPedido, Ambitos.COMPRAS)
                bd.pedidoDao().insertarDetalles(
                    Mapeadores.aEntidadesDetalle(
                        detalles = resultado.valor.detalles,
                        usuarioId = usuarioId,
                        idPedido = idPedido,
                        ambito = Ambitos.COMPRAS,
                        ahora = ahora
                    )
                )
                val productos = Mapeadores.aEntidadesProducto(resultado.valor.detalles, usuarioId, ahora)
                if (productos.isNotEmpty()) bd.productoDao().guardar(productos)
            }
        }
        return resultado
    }

    /**
     * Guarda la propuesta de estado en el dispositivo. NO pisa el estado
     * oficial del servidor: ese siempre tiene prioridad.
     */
    suspend fun registrarEstadoPendienteLocal(
        usuarioId: Long,
        idPedido: Long,
        ambito: String,
        estado: String
    ) = bd.pedidoDao().marcarEstadoPendienteLocal(usuarioId, idPedido, ambito, estado)

    /** Encola el cambio de estado para sincronizarlo con el backend. */
    suspend fun encolarEstadoPedido(
        usuarioId: Long,
        idPedido: Long,
        ambito: String,
        estado: String
    ): EntidadOperacionPendiente = cola.encolar(
        usuarioId = usuarioId,
        tipoOperacion = TiposOperacion.ACTUALIZAR_ESTADO_PEDIDO,
        recursoId = idPedido.toString(),
        payload = mapOf("estado" to estado, "ambito" to ambito),
        claveUnica = "pedido:$ambito:$idPedido:$estado"
    )

    /** Aplica el estado que confirmó el servidor. */
    suspend fun aplicarEstadoServidor(
        usuarioId: Long,
        idPedido: Long,
        ambito: String,
        estado: String?,
        fechaActualizacion: String?,
        estadosPermitidos: String?
    ) = bd.pedidoDao().aplicarEstadoServidor(
        usuarioId = usuarioId,
        idPedido = idPedido,
        ambito = ambito,
        estado = estado,
        fechaActualizacion = Fechas.normalizar(fechaActualizacion),
        estadosPermitidos = estadosPermitidos
    )

    /** El servidor rechazó el cambio: se descarta la propuesta local. */
    suspend fun limpiarEstadoPendiente(usuarioId: Long, idPedido: Long, ambito: String) =
        bd.pedidoDao().limpiarEstadoPendienteLocal(usuarioId, idPedido, ambito)

    /**
     * HU 47 - Consulta el estado vigente del pedido propio y lo aplica a la
     * copia local (el estado del servidor siempre prevalece).
     */
    suspend fun refrescarEstadoPedido(
        usuarioId: Long,
        idPedido: Long
    ): ResultadoApi<EstadoPedidoResponse> {
        val resultado = ejecutarPeticion { api.consultarEstadoPedido(idPedido) }
        if (resultado is ResultadoApi.Exito) {
            val cuerpo = resultado.valor
            bd.pedidoDao().aplicarEstadoServidor(
                usuarioId = usuarioId,
                idPedido = idPedido,
                ambito = Ambitos.COMPRAS,
                estado = cuerpo.estado,
                fechaActualizacion = Fechas.normalizar(cuerpo.fechaActualizacion),
                estadosPermitidos = null
            )
        }
        return resultado
    }

    /** HU 45 - Guarda en la copia local la reseña editada por el usuario. */
    suspend fun actualizarResenaLocal(usuarioId: Long, idPedido: Long, resena: ResenaResponse) =
        bd.withTransaction {
            val entidades = Mapeadores.aEntidadesResena(listOf(resena), usuarioId, idPedido)
            if (entidades.isNotEmpty()) bd.pedidoDao().guardarResenas(entidades)
        }

    /** Borra los pedidos locales del usuario (cierre de sesión). */
    suspend fun borrarUsuario(usuarioId: Long) = bd.withTransaction {
        bd.pedidoDao().borrarPedidosUsuario(usuarioId)
        bd.pedidoDao().borrarDetallesUsuario(usuarioId)
        bd.pedidoDao().borrarResenasUsuario(usuarioId)
        bd.pedidoDao().borrarCalificacionesUsuario(usuarioId)
        bd.productoDao().borrarUsuario(usuarioId)
    }
}

/** Ventas del usuario con los estados que aún no confirmó el servidor. */
data class VentasLocales(
    val pedidos: List<PedidoVendedorResponse>,
    val estadosPendientes: Map<Long, String>
)
