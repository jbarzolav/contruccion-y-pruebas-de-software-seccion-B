package com.ejemplo.publicarproducto.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Consultas y escrituras de la bandeja de notificaciones (HU 53/54).
 *
 * Los filtros y el orden replican exactamente los del backend
 * (`NotificacionService.bandeja`): tipo, estado, rango de fechas y
 * orden estable `fecha DESC, idNotificacion DESC`.
 */
@Dao
interface NotificacionDao {

    @Query(
        """
        SELECT * FROM notificaciones
        WHERE usuarioId = :usuarioId
        ORDER BY fecha DESC, idNotificacion DESC
        """
    )
    fun observarTodas(usuarioId: Long): Flow<List<EntidadNotificacion>>

    @Query(
        """
        SELECT * FROM notificaciones
        WHERE usuarioId = :usuarioId
        ORDER BY fecha DESC, idNotificacion DESC
        """
    )
    suspend fun listarTodas(usuarioId: Long): List<EntidadNotificacion>

    /**
     * Página local de la bandeja con los mismos filtros combinables del
     * backend (tipo, estado y rango de fechas) y el mismo orden estable.
     */
    @Query(
        """
        SELECT * FROM notificaciones
        WHERE usuarioId = :usuarioId
          AND (:tipo IS NULL OR tipo = :tipo)
          AND (:estado IS NULL OR estado = :estado)
          AND (:desde IS NULL OR fecha >= :desde)
          AND (:hasta IS NULL OR fecha <= :hasta)
        ORDER BY fecha DESC, idNotificacion DESC
        LIMIT :limite OFFSET :desplazamiento
        """
    )
    fun observarPagina(
        usuarioId: Long,
        tipo: String?,
        estado: String?,
        desde: String?,
        hasta: String?,
        limite: Int,
        desplazamiento: Int
    ): Flow<List<EntidadNotificacion>>

    @Query("SELECT COUNT(*) FROM notificaciones WHERE usuarioId = :usuarioId AND estado = 'PENDIENTE'")
    fun contarPendientes(usuarioId: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM notificaciones WHERE usuarioId = :usuarioId AND estado = 'PENDIENTE'")
    suspend fun contarPendientesAhora(usuarioId: Long): Long

    @Query(
        """
        SELECT idNotificacion FROM notificaciones
        WHERE usuarioId = :usuarioId AND pendienteLecturaLocal = 1
        ORDER BY fecha DESC, idNotificacion DESC
        """
    )
    fun observarLecturasPendientes(usuarioId: Long): Flow<List<Long>>

    @Query("SELECT * FROM notificaciones WHERE usuarioId = :usuarioId AND idNotificacion = :idNotificacion")
    suspend fun buscar(usuarioId: Long, idNotificacion: Long): EntidadNotificacion?

    @Upsert
    suspend fun guardarTodas(notificaciones: List<EntidadNotificacion>)

    /** Lectura guardada en el dispositivo y pendiente de confirmación del servidor. */
    @Query(
        """
        UPDATE notificaciones
        SET estado = 'LEIDA', fechaLectura = :fechaLectura, pendienteLecturaLocal = 1
        WHERE usuarioId = :usuarioId AND idNotificacion = :idNotificacion
        """
    )
    suspend fun marcarLecturaLocal(usuarioId: Long, idNotificacion: Long, fechaLectura: String)

    @Query(
        """
        UPDATE notificaciones
        SET estado = 'LEIDA', fechaLectura = :fechaLectura, pendienteLecturaLocal = 1
        WHERE usuarioId = :usuarioId AND estado = 'PENDIENTE'
        """
    )
    suspend fun marcarTodasLecturaLocal(usuarioId: Long, fechaLectura: String)

    /** El estado que devuelve el backend siempre prevalece sobre el local. */
    @Query(
        """
        UPDATE notificaciones
        SET estado = :estado, fechaLectura = :fechaLectura, pendienteLecturaLocal = 0
        WHERE usuarioId = :usuarioId AND idNotificacion = :idNotificacion
        """
    )
    suspend fun aplicarEstadoServidor(
        usuarioId: Long,
        idNotificacion: Long,
        estado: String?,
        fechaLectura: String?
    )

    /**
     * Confirmación de la lectura masiva: solo se pisan los avisos que estaban
     * PENDIENTE (o con lectura local pendiente) para no perder la fecha de
     * lectura original de los que ya estaban LEIDA.
     */
    @Query(
        """
        UPDATE notificaciones
        SET estado = 'LEIDA',
            fechaLectura = COALESCE(fechaLectura, :fechaLectura),
            pendienteLecturaLocal = 0
        WHERE usuarioId = :usuarioId AND (estado = 'PENDIENTE' OR pendienteLecturaLocal = 1)
        """
    )
    suspend fun aplicarEstadoServidorGlobal(usuarioId: Long, fechaLectura: String)

    /** El servidor rechazó la lectura: el aviso vuelve a estar pendiente. */
    @Query(
        """
        UPDATE notificaciones
        SET estado = 'PENDIENTE', fechaLectura = NULL, pendienteLecturaLocal = 0
        WHERE usuarioId = :usuarioId AND idNotificacion = :idNotificacion
          AND pendienteLecturaLocal = 1
        """
    )
    suspend fun revertirLecturaLocal(usuarioId: Long, idNotificacion: Long)

    @Query(
        """
        UPDATE notificaciones
        SET estado = 'PENDIENTE', fechaLectura = NULL, pendienteLecturaLocal = 0
        WHERE usuarioId = :usuarioId AND pendienteLecturaLocal = 1
        """
    )
    suspend fun revertirLecturasLocales(usuarioId: Long)

    @Query("DELETE FROM notificaciones WHERE usuarioId = :usuarioId")
    suspend fun borrarUsuario(usuarioId: Long)
}

/** Metadatos de paginación y contador de la bandeja unificada. */
@Dao
interface BandejaDao {

    @Query("SELECT * FROM bandeja_notificaciones WHERE usuarioId = :usuarioId AND claveFiltro = :claveFiltro")
    fun observar(usuarioId: Long, claveFiltro: String): Flow<EntidadBandeja?>

    @Query("SELECT * FROM bandeja_notificaciones WHERE usuarioId = :usuarioId AND claveFiltro = :claveFiltro")
    suspend fun buscar(usuarioId: Long, claveFiltro: String): EntidadBandeja?

    @Upsert
    suspend fun guardar(bandeja: EntidadBandeja)

    /** Guarda la fila de metadatos de una combinación de filtros. */
    @Upsert
    suspend fun guardarTodas(bandejas: List<EntidadBandeja>)

    /**
     * Contador global de pendientes (el backend lo calcula sobre toda la
     * bandeja del usuario, no solo sobre la página visible).
     */
    @Query("SELECT pendientes FROM bandeja_notificaciones WHERE usuarioId = :usuarioId AND claveFiltro = 'GLOBAL'")
    fun observarPendientesGlobales(usuarioId: Long): Flow<Long?>

    @Query("SELECT pendientes FROM bandeja_notificaciones WHERE usuarioId = :usuarioId AND claveFiltro = 'GLOBAL'")
    suspend fun buscarPendientesGlobales(usuarioId: Long): Long?

    @Query("UPDATE bandeja_notificaciones SET pendientes = :pendientes WHERE usuarioId = :usuarioId")
    suspend fun actualizarPendientes(usuarioId: Long, pendientes: Long)

    @Query("DELETE FROM bandeja_notificaciones WHERE usuarioId = :usuarioId")
    suspend fun borrarUsuario(usuarioId: Long)
}

/** Pedidos (compras y ventas), líneas, reseñas y calificaciones. */
@Dao
interface PedidoDao {

    @Query(
        """
        SELECT * FROM pedidos
        WHERE usuarioId = :usuarioId AND ambito = :ambito
        ORDER BY fecha DESC, idPedido DESC
        """
    )
    fun observarPedidos(usuarioId: Long, ambito: String): Flow<List<EntidadPedido>>

    @Query(
        """
        SELECT * FROM pedidos
        WHERE usuarioId = :usuarioId AND ambito = :ambito
        ORDER BY fecha DESC, idPedido DESC
        """
    )
    suspend fun listarPedidos(usuarioId: Long, ambito: String): List<EntidadPedido>

    @Query(
        """
        SELECT * FROM pedidos
        WHERE usuarioId = :usuarioId AND idPedido = :idPedido AND ambito = :ambito
        """
    )
    fun observarPedido(usuarioId: Long, idPedido: Long, ambito: String): Flow<EntidadPedido?>

    @Query(
        """
        SELECT * FROM pedidos
        WHERE usuarioId = :usuarioId AND idPedido = :idPedido AND ambito = :ambito
        """
    )
    suspend fun buscarPedido(usuarioId: Long, idPedido: Long, ambito: String): EntidadPedido?

    @Upsert
    suspend fun guardarPedidos(pedidos: List<EntidadPedido>)

    /** El estado que devuelve el backend siempre prevalece sobre el local. */
    @Query(
        """
        UPDATE pedidos
        SET estado = :estado,
            fechaActualizacion = :fechaActualizacion,
            estadosPermitidos = COALESCE(:estadosPermitidos, estadosPermitidos),
            estadoPendienteLocal = NULL
        WHERE usuarioId = :usuarioId AND idPedido = :idPedido AND ambito = :ambito
        """
    )
    suspend fun aplicarEstadoServidor(
        usuarioId: Long,
        idPedido: Long,
        ambito: String,
        estado: String?,
        fechaActualizacion: String?,
        estadosPermitidos: String?
    )

    /**
     * Estado propuesto en el dispositivo que el servidor todavía no validó.
     * NO pisa el estado oficial: el backend siempre tiene prioridad.
     */
    @Query(
        """
        UPDATE pedidos
        SET estadoPendienteLocal = :estado
        WHERE usuarioId = :usuarioId AND idPedido = :idPedido AND ambito = :ambito
        """
    )
    suspend fun marcarEstadoPendienteLocal(
        usuarioId: Long,
        idPedido: Long,
        ambito: String,
        estado: String
    )

    @Query(
        """
        UPDATE pedidos
        SET estadoPendienteLocal = NULL
        WHERE usuarioId = :usuarioId AND idPedido = :idPedido AND ambito = :ambito
        """
    )
    suspend fun limpiarEstadoPendienteLocal(usuarioId: Long, idPedido: Long, ambito: String)

    @Query("DELETE FROM pedidos WHERE usuarioId = :usuarioId")
    suspend fun borrarPedidosUsuario(usuarioId: Long)

    @Query("DELETE FROM detalles_pedido WHERE usuarioId = :usuarioId AND idPedido = :idPedido AND ambito = :ambito")
    suspend fun borrarDetalles(usuarioId: Long, idPedido: Long, ambito: String)

    @Query(
        """
        SELECT * FROM detalles_pedido
        WHERE usuarioId = :usuarioId AND idPedido = :idPedido AND ambito = :ambito
        ORDER BY idProducto ASC
        """
    )
    fun observarDetalles(usuarioId: Long, idPedido: Long, ambito: String): Flow<List<EntidadDetallePedido>>

    @Query(
        """
        SELECT * FROM detalles_pedido
        WHERE usuarioId = :usuarioId AND idPedido = :idPedido AND ambito = :ambito
        ORDER BY idProducto ASC
        """
    )
    suspend fun listarDetalles(usuarioId: Long, idPedido: Long, ambito: String): List<EntidadDetallePedido>

    @Query("SELECT * FROM detalles_pedido WHERE usuarioId = :usuarioId ORDER BY idPedido ASC, idProducto ASC")
    suspend fun listarDetallesUsuario(usuarioId: Long): List<EntidadDetallePedido>

    @Query("SELECT * FROM detalles_pedido WHERE usuarioId = :usuarioId ORDER BY idPedido ASC, idProducto ASC")
    fun observarDetallesUsuario(usuarioId: Long): Flow<List<EntidadDetallePedido>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarDetalles(detalles: List<EntidadDetallePedido>)

    @Query("DELETE FROM detalles_pedido WHERE usuarioId = :usuarioId")
    suspend fun borrarDetallesUsuario(usuarioId: Long)

    @Query("SELECT * FROM pedido_resenas WHERE usuarioId = :usuarioId AND idPedido = :idPedido")
    fun observarResenas(usuarioId: Long, idPedido: Long): Flow<List<EntidadResena>>

    @Query("SELECT * FROM pedido_resenas WHERE usuarioId = :usuarioId AND idPedido = :idPedido")
    suspend fun listarResenas(usuarioId: Long, idPedido: Long): List<EntidadResena>

    @Query("SELECT * FROM pedido_resenas WHERE usuarioId = :usuarioId ORDER BY idPedido ASC")
    suspend fun listarResenasUsuario(usuarioId: Long): List<EntidadResena>

    @Query("SELECT * FROM pedido_resenas WHERE usuarioId = :usuarioId ORDER BY idPedido ASC")
    fun observarResenasUsuario(usuarioId: Long): Flow<List<EntidadResena>>

    @Upsert
    suspend fun guardarResenas(resenas: List<EntidadResena>)

    @Query("DELETE FROM pedido_resenas WHERE usuarioId = :usuarioId AND idPedido = :idPedido")
    suspend fun borrarResenas(usuarioId: Long, idPedido: Long)

    @Query("DELETE FROM pedido_resenas WHERE usuarioId = :usuarioId")
    suspend fun borrarResenasUsuario(usuarioId: Long)

    @Query("SELECT * FROM pedido_calificaciones WHERE usuarioId = :usuarioId AND idPedido = :idPedido")
    suspend fun listarCalificaciones(usuarioId: Long, idPedido: Long): List<EntidadCalificacion>

    @Query("SELECT * FROM pedido_calificaciones WHERE usuarioId = :usuarioId ORDER BY idPedido ASC")
    suspend fun listarCalificacionesUsuario(usuarioId: Long): List<EntidadCalificacion>

    @Query("SELECT * FROM pedido_calificaciones WHERE usuarioId = :usuarioId ORDER BY idPedido ASC")
    fun observarCalificacionesUsuario(usuarioId: Long): Flow<List<EntidadCalificacion>>

    @Upsert
    suspend fun guardarCalificaciones(calificaciones: List<EntidadCalificacion>)

    @Query("DELETE FROM pedido_calificaciones WHERE usuarioId = :usuarioId AND idPedido = :idPedido")
    suspend fun borrarCalificaciones(usuarioId: Long, idPedido: Long)

    @Query("DELETE FROM pedido_calificaciones WHERE usuarioId = :usuarioId")
    suspend fun borrarCalificacionesUsuario(usuarioId: Long)
}

/** Productos y precios conocidos para consultar pedidos sin conexión. */
@Dao
interface ProductoDao {

    @Query("SELECT * FROM productos WHERE usuarioId = :usuarioId ORDER BY nombre ASC")
    fun observar(usuarioId: Long): Flow<List<EntidadProducto>>

    @Query("SELECT * FROM productos WHERE usuarioId = :usuarioId AND idProducto = :idProducto")
    suspend fun buscar(usuarioId: Long, idProducto: Long): EntidadProducto?

    @Upsert
    suspend fun guardar(productos: List<EntidadProducto>)

    @Query("DELETE FROM productos WHERE usuarioId = :usuarioId")
    suspend fun borrarUsuario(usuarioId: Long)
}

/**
 * Cola persistente de operaciones pendientes de sincronizar.
 *
 * La deduplicación se resuelve en el repositorio (misma clave y mismo estado
 * activo) para que una operación rechazada pueda volver a encolarse de forma
 * explícita sin duplicar las que ya están en cola.
 */
@Dao
interface OperacionPendienteDao {

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE usuarioId = :usuarioId AND estado IN ('PENDIENTE', 'EN_CURSO', 'REQUIERE_SESION')
        ORDER BY fechaCreacion ASC, id ASC
        """
    )
    fun observarPendientes(usuarioId: Long): Flow<List<EntidadOperacionPendiente>>

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE usuarioId = :usuarioId AND estado IN ('PENDIENTE', 'EN_CURSO', 'REQUIERE_SESION')
        ORDER BY fechaCreacion ASC, id ASC
        """
    )
    suspend fun listarPendientes(usuarioId: Long): List<EntidadOperacionPendiente>

    @Query(
        """
        SELECT COUNT(*) FROM operaciones_pendientes
        WHERE usuarioId = :usuarioId AND estado IN ('PENDIENTE', 'EN_CURSO', 'REQUIERE_SESION')
        """
    )
    fun contarPendientes(usuarioId: Long): Flow<Long>

    @Query(
        """
        SELECT * FROM operaciones_pendientes
        WHERE usuarioId = :usuarioId AND claveUnica = :claveUnica
          AND estado IN ('PENDIENTE', 'EN_CURSO', 'REQUIERE_SESION')
        LIMIT 1
        """
    )
    suspend fun buscarActivaPorClave(usuarioId: Long, claveUnica: String): EntidadOperacionPendiente?

    @Query("SELECT * FROM operaciones_pendientes WHERE usuarioId = :usuarioId ORDER BY fechaCreacion DESC, id DESC")
    fun observarTodas(usuarioId: Long): Flow<List<EntidadOperacionPendiente>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(operacion: EntidadOperacionPendiente): Long

    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = :estado, intentos = intentos + 1, fechaUltimoIntento = :fecha, ultimoMensajeError = :mensaje
        WHERE id = :id
        """
    )
    suspend fun registrarIntento(id: Long, estado: String, fecha: Long, mensaje: String?)

    /**
     * Actualiza el resultado del intento que ya se contabilizó en
     * `registrarIntento`: no vuelve a incrementar el contador.
     */
    @Query(
        """
        UPDATE operaciones_pendientes
        SET estado = :estado, fechaUltimoIntento = :fecha, ultimoMensajeError = :mensaje
        WHERE id = :id
        """
    )
    suspend fun actualizarIntento(id: Long, estado: String, fecha: Long, mensaje: String?)

    @Query("UPDATE operaciones_pendientes SET estado = :estado, ultimoMensajeError = :mensaje WHERE id = :id")
    suspend fun actualizarEstado(id: Long, estado: String, mensaje: String?)

    @Query("DELETE FROM operaciones_pendientes WHERE usuarioId = :usuarioId AND estado = 'CONFIRMADA'")
    suspend fun borrarConfirmadas(usuarioId: Long)

    @Query("DELETE FROM operaciones_pendientes WHERE usuarioId = :usuarioId")
    suspend fun borrarUsuario(usuarioId: Long)
}

/** Información de sincronización (fecha, resultado y mensaje) por recurso. */
@Dao
interface MetadatoSincronizacionDao {

    @Query("SELECT * FROM metadatos_sincronizacion WHERE usuarioId = :usuarioId AND recurso = :recurso")
    fun observar(usuarioId: Long, recurso: String): Flow<EntidadMetadatoSincronizacion?>

    @Query("SELECT * FROM metadatos_sincronizacion WHERE usuarioId = :usuarioId AND recurso = :recurso")
    suspend fun buscar(usuarioId: Long, recurso: String): EntidadMetadatoSincronizacion?

    @Upsert
    suspend fun guardar(metadato: EntidadMetadatoSincronizacion)

    @Query("DELETE FROM metadatos_sincronizacion WHERE usuarioId = :usuarioId")
    suspend fun borrarUsuario(usuarioId: Long)
}
