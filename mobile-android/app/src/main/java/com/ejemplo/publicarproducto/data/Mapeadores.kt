package com.ejemplo.publicarproducto.data

import com.ejemplo.publicarproducto.local.Ambitos
import com.ejemplo.publicarproducto.local.EntidadBandeja
import com.ejemplo.publicarproducto.local.EntidadCalificacion
import com.ejemplo.publicarproducto.local.EntidadDetallePedido
import com.ejemplo.publicarproducto.local.EntidadNotificacion
import com.ejemplo.publicarproducto.local.EntidadPedido
import com.ejemplo.publicarproducto.local.EntidadProducto
import com.ejemplo.publicarproducto.local.EntidadResena
import com.ejemplo.publicarproducto.model.BandejaNotificacionesResponse
import com.ejemplo.publicarproducto.model.CalificacionResponse
import com.ejemplo.publicarproducto.model.DetallePedidoResponse
import com.ejemplo.publicarproducto.model.NotificacionResponse
import com.ejemplo.publicarproducto.model.PedidoDetalleResponse
import com.ejemplo.publicarproducto.model.PedidoResponse
import com.ejemplo.publicarproducto.model.PedidoVendedorResponse
import com.ejemplo.publicarproducto.model.ResenaResponse
import java.math.BigDecimal

/**
 * Conversión entre los DTO del backend (Gson) y las entidades de Room.
 * Así la base de datos local no depende del formato JSON de la API.
 *
 * Los DTO del backend se reutilizan tal cual: no se duplican modelos.
 */
object Mapeadores {

    // ---------- Notificaciones ----------

    fun aEntidad(
        dto: NotificacionResponse,
        usuarioId: Long,
        ahora: Long,
        pendienteLecturaLocal: Boolean = false
    ): EntidadNotificacion = EntidadNotificacion(
        idNotificacion = dto.idNotificacion ?: 0L,
        usuarioId = usuarioId,
        tipo = dto.tipo,
        resumen = dto.resumen,
        fecha = Fechas.normalizar(dto.fecha),
        estado = if (pendienteLecturaLocal) "LEIDA" else dto.estado,
        idPedido = dto.idPedido,
        enlace = dto.enlace,
        idConversacion = dto.idConversacion,
        fechaLectura = if (pendienteLecturaLocal) Fechas.ahora() else Fechas.normalizar(dto.fechaLectura),
        fechaSincronizacion = ahora,
        pendienteLecturaLocal = pendienteLecturaLocal
    )

    fun aDto(entidad: EntidadNotificacion): NotificacionResponse = NotificacionResponse(
        idNotificacion = entidad.idNotificacion,
        tipo = entidad.tipo,
        resumen = entidad.resumen,
        fecha = entidad.fecha,
        estado = entidad.estado,
        idPedido = entidad.idPedido,
        enlace = entidad.enlace,
        idConversacion = entidad.idConversacion,
        fechaLectura = entidad.fechaLectura
    )

    /** Metadatos de la página local de la bandeja. */
    fun aEntidadBandeja(
        usuarioId: Long,
        claveFiltro: String,
        respuesta: BandejaNotificacionesResponse,
        ahora: Long
    ): EntidadBandeja = EntidadBandeja(
        usuarioId = usuarioId,
        claveFiltro = claveFiltro,
        pagina = respuesta.pagina,
        tamano = respuesta.tamano,
        totalElementos = respuesta.totalElementos,
        totalPaginas = respuesta.totalPaginas,
        tieneSiguiente = respuesta.tieneSiguiente,
        pendientes = respuesta.pendientes,
        fechaSincronizacion = ahora
    )

    // ---------- Pedidos ----------

    fun aEntidad(
        dto: PedidoResponse,
        usuarioId: Long,
        ambito: String,
        ahora: Long,
        estadoPendienteLocal: String? = null
    ): EntidadPedido? {
        val idPedido = dto.idPedido ?: return null
        return EntidadPedido(
            idPedido = idPedido,
            usuarioId = usuarioId,
            ambito = ambito,
            clienteId = dto.clienteId,
            fecha = Fechas.normalizar(dto.fecha),
            total = dto.total?.toPlainString(),
            estado = dto.estado,
            fechaActualizacion = Fechas.normalizar(dto.fechaActualizacion),
            estadosPermitidos = null,
            estadoPendienteLocal = estadoPendienteLocal,
            fechaSincronizacion = ahora
        )
    }

    fun aEntidad(
        dto: PedidoVendedorResponse,
        usuarioId: Long,
        ahora: Long,
        estadoPendienteLocal: String? = null
    ): EntidadPedido = EntidadPedido(
        idPedido = dto.idPedido,
        usuarioId = usuarioId,
        ambito = Ambitos.VENTAS,
        clienteId = null,
        fecha = Fechas.normalizar(dto.fecha),
        total = dto.total.toPlainString(),
        estado = dto.estado,
        fechaActualizacion = Fechas.normalizar(dto.fechaActualizacion),
        estadosPermitidos = dto.estadosPermitidos?.joinToString(","),
        estadoPendienteLocal = estadoPendienteLocal,
        fechaSincronizacion = ahora
    )

    fun aEntidad(
        dto: PedidoDetalleResponse,
        usuarioId: Long,
        ahora: Long,
        estadoPendienteLocal: String? = null
    ): EntidadPedido? {
        val idPedido = dto.idPedido ?: return null
        return EntidadPedido(
            idPedido = idPedido,
            usuarioId = usuarioId,
            ambito = Ambitos.COMPRAS,
            clienteId = dto.clienteId,
            fecha = Fechas.normalizar(dto.fecha),
            total = dto.total?.toPlainString(),
            estado = dto.estado,
            fechaActualizacion = Fechas.normalizar(dto.fechaActualizacion),
            estadosPermitidos = null,
            estadoPendienteLocal = estadoPendienteLocal,
            fechaSincronizacion = ahora
        )
    }

    fun aEntidadesDetalle(
        detalles: List<DetallePedidoResponse>?,
        usuarioId: Long,
        idPedido: Long,
        ambito: String,
        ahora: Long
    ): List<EntidadDetallePedido> = detalles.orEmpty().mapNotNull { detalle ->
        val idProducto = detalle.idProducto ?: return@mapNotNull null
        EntidadDetallePedido(
            usuarioId = usuarioId,
            idPedido = idPedido,
            ambito = ambito,
            idProducto = idProducto,
            nombreProducto = detalle.nombreProducto,
            cantidad = detalle.cantidad,
            precioUnitario = detalle.precioUnitario?.toPlainString(),
            subtotal = detalle.subtotal?.toPlainString(),
            fechaSincronizacion = ahora
        )
    }.distinctBy { it.idProducto }

    fun aEntidadesProducto(
        detalles: List<DetallePedidoResponse>?,
        usuarioId: Long,
        ahora: Long
    ): List<EntidadProducto> = detalles.orEmpty().mapNotNull { detalle ->
        val idProducto = detalle.idProducto ?: return@mapNotNull null
        EntidadProducto(
            idProducto = idProducto,
            usuarioId = usuarioId,
            nombre = detalle.nombreProducto,
            precio = detalle.precioUnitario?.toPlainString(),
            fechaSincronizacion = ahora
        )
    }.distinctBy { it.idProducto }

    fun aEntidadesResena(
        resenas: List<ResenaResponse>?,
        usuarioId: Long,
        idPedido: Long
    ): List<EntidadResena> = resenas.orEmpty().mapNotNull { resena ->
        val idResena = resena.id ?: return@mapNotNull null
        EntidadResena(
            idResena = idResena,
            usuarioId = usuarioId,
            idPedido = idPedido,
            destino = resena.destino,
            productoId = resena.productoId,
            vendedorId = resena.vendedorId,
            texto = resena.texto,
            fecha = Fechas.normalizar(resena.fecha),
            fechaModificacion = Fechas.normalizar(resena.fechaModificacion)
        )
    }

    fun aEntidadesCalificacion(
        calificacion: CalificacionResponse?,
        usuarioId: Long,
        idPedido: Long
    ): List<EntidadCalificacion> {
        val actual = calificacion ?: return emptyList()
        val id = actual.id ?: return emptyList()
        return listOf(
            EntidadCalificacion(
                id = id,
                usuarioId = usuarioId,
                idPedido = idPedido,
                valor = actual.valor,
                fecha = Fechas.normalizar(actual.fecha)
            )
        )
    }

    fun aDtoDetalle(entidad: EntidadDetallePedido): DetallePedidoResponse = DetallePedidoResponse(
        idProducto = entidad.idProducto,
        nombreProducto = entidad.nombreProducto,
        cantidad = entidad.cantidad,
        precioUnitario = entidad.precioUnitario?.toBigDecimalOrNull(),
        subtotal = entidad.subtotal?.toBigDecimalOrNull()
    )

    fun aDtoResena(entidad: EntidadResena): ResenaResponse = ResenaResponse(
        id = entidad.idResena,
        idPedido = entidad.idPedido,
        destino = entidad.destino,
        productoId = entidad.productoId,
        vendedorId = entidad.vendedorId,
        texto = entidad.texto,
        fecha = entidad.fecha,
        fechaModificacion = entidad.fechaModificacion
    )

    fun aDtoCalificacion(entidad: EntidadCalificacion): CalificacionResponse = CalificacionResponse(
        id = entidad.id,
        idPedido = entidad.idPedido,
        valor = entidad.valor,
        fecha = entidad.fecha
    )

    fun aDto(
        pedido: EntidadPedido,
        detalles: List<EntidadDetallePedido>,
        resenas: List<EntidadResena>,
        calificacion: EntidadCalificacion?
    ): PedidoResponse = PedidoResponse(
        idPedido = pedido.idPedido,
        clienteId = pedido.clienteId,
        fecha = pedido.fecha,
        total = pedido.total?.toBigDecimalOrNull(),
        detalles = detalles.map { aDtoDetalle(it) },
        calificacion = calificacion?.let { aDtoCalificacion(it) },
        resenas = resenas.map { aDtoResena(it) },
        estado = pedido.estado,
        fechaActualizacion = pedido.fechaActualizacion
    )

    fun aDtoPedidoDetalle(
        pedido: EntidadPedido,
        detalles: List<EntidadDetallePedido>
    ): PedidoDetalleResponse = PedidoDetalleResponse(
        idPedido = pedido.idPedido,
        clienteId = pedido.clienteId,
        fecha = pedido.fecha,
        estado = pedido.estado,
        fechaActualizacion = pedido.fechaActualizacion,
        total = pedido.total?.toBigDecimalOrNull(),
        detalles = detalles.map { aDtoDetalle(it) }
    )

    fun aDtoPedidoVendedor(
        pedido: EntidadPedido,
        detalles: List<EntidadDetallePedido>
    ): PedidoVendedorResponse = PedidoVendedorResponse(
        idPedido = pedido.idPedido,
        fecha = pedido.fecha,
        estado = pedido.estado.orEmpty(),
        fechaActualizacion = pedido.fechaActualizacion,
        total = pedido.total?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
        productos = detalles.map { aDtoDetalle(it) },
        estadosPermitidos = pedido.estadosPermitidos
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
    )
}
