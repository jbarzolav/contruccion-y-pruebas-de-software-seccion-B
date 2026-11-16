package com.ejemplo.publicarproducto.model.repository

import androidx.room.withTransaction
import com.ejemplo.publicarproducto.model.local.Ambitos
import com.ejemplo.publicarproducto.model.local.EntidadOperacionPendiente
import com.ejemplo.publicarproducto.model.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.model.local.TiposOperacion
import com.ejemplo.publicarproducto.model.CalificacionRequest
import com.ejemplo.publicarproducto.model.ErrorResponse
import com.ejemplo.publicarproducto.model.EstadoPedidoResponse
import com.ejemplo.publicarproducto.model.PedidoDetalleResponse
import com.ejemplo.publicarproducto.model.PedidoResponse
import com.ejemplo.publicarproducto.model.PedidoVendedorResponse
import com.ejemplo.publicarproducto.model.ResenaEdicionRequest
import com.ejemplo.publicarproducto.model.ResenaRequest
import com.ejemplo.publicarproducto.model.ResenaResponse
import com.ejemplo.publicarproducto.model.mapper.Fechas
import com.ejemplo.publicarproducto.model.mapper.Mapeadores
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.remote.ejecutarPeticion
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import retrofit2.Response

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
                val productos = mutableListOf<com.ejemplo.publicarproducto.model.local.EntidadProducto>()
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
                val productos = mutableListOf<com.ejemplo.publicarproducto.model.local.EntidadProducto>()
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

    /**
     * HU 45 - Edita el texto de una reseña propia (HU 45).
     *
     * Encapsula la coordinación completa que antes hacía el ViewModel:
     *
     * 1. envía la modificación al backend con Retrofit (`PUT api/resenas/{id}`);
     * 2. interpreta el resultado;
     * 3. solo si el servidor confirma, actualiza la copia local con
     *    [actualizarResenaLocal] (Room);
     * 4. devuelve el resultado al ViewModel.
     *
     * El ORDEN es el del código original: primero Retrofit, después Room.
     * Si el backend falla, Room no se toca y la reseña anterior se conserva.
     * Si la escritura local falla, el error se informa sin ocultarlo.
     */
    suspend fun editarResena(
        usuarioId: Long,
        idPedido: Long,
        idResena: Long,
        texto: String
    ): ResultadoApi<ResenaResponse> =
        editarResenaConApi(api, idResena, texto) { resena ->
            actualizarResenaLocal(usuarioId, idPedido, resena)
        }

    /** Borra los pedidos locales del usuario (cierre de sesión). */
    suspend fun borrarUsuario(usuarioId: Long) = bd.withTransaction {
        bd.pedidoDao().borrarPedidosUsuario(usuarioId)
        bd.pedidoDao().borrarDetallesUsuario(usuarioId)
        bd.pedidoDao().borrarResenasUsuario(usuarioId)
        bd.pedidoDao().borrarCalificacionesUsuario(usuarioId)
        bd.productoDao().borrarUsuario(usuarioId)
    }

    // ==================================================================
    // Checkout (HU 15, HU 41, HU 42 y HU 59.4)
    //
    // Son operaciones EN LÍNEA: no escriben en Room y NO se encolan.
    // El registro del pedido se envía con la cabecera "Idempotency-Key"
    // (HU 59.4): reintentar con la MISMA clave devuelve el mismo pedido
    // sin duplicarlo ni descontar stock dos veces, y con una clave nueva
    // se trata como una compra distinta. Aun así el checkout sigue siendo
    // bloqueante y en línea, para que la interfaz muestre el comprobante
    // apenas el backend responde.
    // ==================================================================

    /**
     * HU 15 - Registra el pedido con `POST /api/pedidos` y devuelve el
     * comprobante. El backend vacía el carrito al aceptarlo.
     *
     * HU 59.4 - `claveIdempotencia` viaja como cabecera `Idempotency-Key`:
     * el mismo valor debe reenviarse si la confirmación quedó incierta
     * (timeout o corte de red) para recuperar el pedido ya registrado.
     */
    suspend fun confirmarCompra(claveIdempotencia: String? = null): ResultadoApi<PedidoResponse> =
        ejecutarPeticion(
            excepcion = { e ->
                "No hay conexión con el backend (10.0.2.2:9000): ${e.message}"
            },
            porCodigo = { "Error $it: no se pudo registrar el pedido." },
            leerMensajes = { true },
            formatearMensajes = { lista -> lista.firstOrNull().orEmpty() }
        ) { api.confirmarCompra(claveIdempotencia) }

    /**
     * HU 41 - Califica el pedido propio recién comprado.
     */
    suspend fun calificarPedido(
        idPedido: Long,
        valor: Int,
    ): ResultadoApi<PedidoResponse> =
        ejecutarPeticion(
            excepcion = { e -> e.message ?: "No se pudo guardar la calificación." },
            porCodigo = { "Error $it: no se pudo registrar el pedido." },
            leerMensajes = { true },
            formatearMensajes = { lista -> lista.firstOrNull().orEmpty() }
        ) { api.calificarPedido(idPedido, CalificacionRequest(valor)) }

    /**
     * HU 42 - Publica la reseña del pedido recién comprado.
     */
    suspend fun resenarPedido(
        idPedido: Long,
        destino: String,
        texto: String,
    ): ResultadoApi<PedidoResponse> =
        ejecutarPeticion(
            excepcion = { e -> e.message ?: "No se pudo publicar la reseña." },
            porCodigo = { "Error $it: no se pudo registrar el pedido." },
            leerMensajes = { true },
            formatearMensajes = { lista -> lista.firstOrNull().orEmpty() }
        ) { api.resenarPedido(idPedido, ResenaRequest(destino, texto)) }
}

typealias VentasLocales = com.ejemplo.publicarproducto.model.VentasLocales

// ==================================================================
// HU 45 - Edición de reseñas
// ==================================================================

/**
 * HU 45 - Coordinación de la edición de una reseña: **Retrofit primero,
 * Room después** (mismo orden y mismos textos que el código original de
 * `MisPedidosViewModel` antes del grupo D-3).
 *
 * @param api interfaz real de Retrofit (`PUT api/resenas/{idResena}`).
 * @param guardarLocal persistencia de la reseña que confirmó el servidor;
 * en producción es `PedidosRepositorio.actualizarResenaLocal`. Se recibe
 * como parámetro para poder verificar en la JVM (sin Room) el orden de las
 * operaciones y el comportamiento ante un fallo local.
 *
 * Textos de error conservados exactamente:
 * - primer elemento de `messages[]` cuando existe;
 * - `"Error N al consultar pedidos."` cuando no hay mensajes (incluido un
 *   2xx sin cuerpo);
 * - `e.message` de la excepción o, si no tiene mensaje,
 *   `"No se pudo actualizar la reseña."`.
 *
 * `CancellationException` siempre se vuelve a lanzar.
 */
internal suspend fun editarResenaConApi(
    api: ProductoApi,
    idResena: Long,
    texto: String,
    guardarLocal: suspend (ResenaResponse) -> Unit
): ResultadoApi<ResenaResponse> {
    val respuesta = try {
        api.editarResena(idResena, ResenaEdicionRequest(texto))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return ResultadoApi.Error(
            codigo = null,
            mensaje = e.message ?: "No se pudo actualizar la reseña.",
            temporal = true
        )
    }
    if (respuesta.isSuccessful && respuesta.body() != null) {
        val actualizada = respuesta.body()!!
        return try {
            guardarLocal(actualizada)
            ResultadoApi.Exito(actualizada)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // La escritura local falló: se informa sin ocultar el error y
            // sin afirmar que la reseña se guardó.
            ResultadoApi.Error(
                codigo = null,
                mensaje = e.message ?: "No se pudo actualizar la reseña.",
                temporal = false
            )
        }
    }
    val codigo = respuesta.code()
    return ResultadoApi.Error(
        codigo = codigo,
        mensaje = mensajeDeEdicion(respuesta),
        temporal = codigo >= 500 || codigo == 408 || codigo == 429,
        sesionExpirada = codigo == 401
    )
}

/** Texto de error de la edición de reseña, idéntico al de la pantalla. */
private fun mensajeDeEdicion(respuesta: Response<*>): String = try {
    val cuerpo = respuesta.errorBody()?.string()
    Gson().fromJson(cuerpo, ErrorResponse::class.java)
        ?.messages?.firstOrNull()
        ?: "Error ${respuesta.code()} al consultar pedidos."
} catch (_: Exception) {
    "Error ${respuesta.code()} al consultar pedidos."
}
