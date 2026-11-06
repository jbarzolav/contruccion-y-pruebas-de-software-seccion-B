package com.ejemplo.publicarproducto.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Mejora técnica Offline First (agregado independiente, no es una HU).
 *
 * Copia local persistente de la información oficial que vive en MariaDB.
 * El backend SIEMPRE tiene prioridad: cada sincronización sobrescribe la
 * copia local con la respuesta real del servidor.
 *
 * Todas las tablas llevan `usuarioId` y lo forman parte de la clave primaria:
 * así un usuario NUNCA puede leer la información local de otro, ni siquiera
 * por error de consulta. Ninguna tabla guarda tokens ni contraseñas (eso vive
 * en SharedPreferences).
 */
object Ambitos {
    /** Pedidos de compra del usuario (HU 46/50). */
    const val COMPRAS = "COMPRAS"

    /** Pedidos que incluyen productos del usuario vendedor (HU 48/49). */
    const val VENTAS = "VENTAS"
}

/**
 * Aviso de la bandeja unificada (HU 53) con su estado de lectura (HU 54).
 *
 * `pendienteLecturaLocal` marca que la lectura se guardó en el dispositivo
 * pero todavía NO la confirmó el servidor: la interfaz nunca afirma que el
 * servidor confirmó una operación que solo existe localmente.
 */
@Entity(
    tableName = "notificaciones",
    primaryKeys = ["usuarioId", "idNotificacion"],
    indices = [
        Index(value = ["usuarioId", "estado"]),
        Index(value = ["usuarioId", "tipo"])
    ]
)
data class EntidadNotificacion(
    val usuarioId: Long,
    val idNotificacion: Long,
    val tipo: String?,
    val resumen: String?,
    val fecha: String?,
    val estado: String?,
    val idPedido: Long?,
    val enlace: String?,
    val idConversacion: Long?,
    val fechaLectura: String?,
    val fechaSincronizacion: Long,
    @ColumnInfo(defaultValue = "0")
    val pendienteLecturaLocal: Boolean = false
)

/**
 * Metadatos de la bandeja unificada (HU 53): paginación y contador global.
 * Se guardan por usuario y por combinación de filtros para poder reconstruir
 * la vista sin conexión.
 *
 * La fila con `claveFiltro = "GLOBAL"` conserva el contador de pendientes de
 * toda la bandeja (el backend lo calcula sobre todos los avisos del usuario,
 * no solo sobre la página visible).
 */
@Entity(
    tableName = "bandeja_notificaciones",
    primaryKeys = ["usuarioId", "claveFiltro"]
)
data class EntidadBandeja(
    val usuarioId: Long,
    val claveFiltro: String,
    val pagina: Int,
    val tamano: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val tieneSiguiente: Boolean,
    val pendientes: Long,
    val fechaSincronizacion: Long
)

/**
 * Pedido del usuario. La misma tabla guarda las compras (HU 46/50) y los
 * pedidos recibidos como vendedor (HU 48/49) usando `ambito`.
 *
 * `estadoPendienteLocal` es el estado propuesto por el usuario que aún no
 * confirmó el servidor; el estado oficial siempre gana cuando llega la
 * respuesta del backend.
 */
@Entity(
    tableName = "pedidos",
    primaryKeys = ["usuarioId", "idPedido", "ambito"],
    indices = [Index(value = ["usuarioId", "ambito"])]
)
data class EntidadPedido(
    val usuarioId: Long,
    val idPedido: Long,
    val ambito: String,
    val clienteId: Long?,
    val fecha: String?,
    val total: String?,
    val estado: String?,
    val fechaActualizacion: String?,
    val estadosPermitidos: String?,
    val estadoPendienteLocal: String?,
    val fechaSincronizacion: Long
)

/**
 * Línea de pedido con el precio histórico congelado en la operación
 * (HU 15/50). Permite consultar el pedido y sus productos sin conexión.
 *
 * `ambito` forma parte de la clave: como comprador se guardan todas las
 * líneas y como vendedor solo las suyas, son conjuntos distintos del mismo
 * pedido.
 */
@Entity(
    tableName = "detalles_pedido",
    primaryKeys = ["usuarioId", "idPedido", "ambito", "idProducto"],
    indices = [
        Index(value = ["usuarioId", "idPedido"]),
        Index(value = ["usuarioId", "idProducto"])
    ]
)
data class EntidadDetallePedido(
    val usuarioId: Long,
    val idPedido: Long,
    val ambito: String,
    val idProducto: Long,
    val nombreProducto: String?,
    val cantidad: Int?,
    val precioUnitario: String?,
    val subtotal: String?,
    val fechaSincronizacion: Long
)

/**
 * Producto y su último precio conocido, necesario para consultar los pedidos
 * sin conexión. El precio histórico por línea queda en `detalles_pedido`.
 */
@Entity(
    tableName = "productos",
    primaryKeys = ["usuarioId", "idProducto"]
)
data class EntidadProducto(
    val usuarioId: Long,
    val idProducto: Long,
    val nombre: String?,
    val precio: String?,
    val fechaSincronizacion: Long
)

/**
 * Reseña del pedido (HU 41/42/45) para que la lista de compras conserve
 * el contenido editable también sin conexión.
 */
@Entity(
    tableName = "pedido_resenas",
    primaryKeys = ["usuarioId", "idResena"],
    indices = [Index(value = ["usuarioId", "idPedido"])]
)
data class EntidadResena(
    val usuarioId: Long,
    val idResena: Long,
    val idPedido: Long,
    val destino: String?,
    val productoId: Long?,
    val vendedorId: Long?,
    val texto: String?,
    val fecha: String?,
    val fechaModificacion: String?
)

/** Calificación del pedido (HU 41/42). */
@Entity(
    tableName = "pedido_calificaciones",
    primaryKeys = ["usuarioId", "id"],
    indices = [Index(value = ["usuarioId", "idPedido"])]
)
data class EntidadCalificacion(
    val usuarioId: Long,
    val id: Long,
    val idPedido: Long,
    val valor: Int?,
    val fecha: String?
)

/**
 * Operación realizada sin conexión que todavía no confirmó el servidor.
 *
 * La cola es persistente (no vive en memoria) para que no se pierda al
 * cerrar la aplicación. `claveUnica` evita duplicados al reintentar: dos
 * intentos de la misma operación no generan dos filas activas.
 */
@Entity(
    tableName = "operaciones_pendientes",
    indices = [
        Index(value = ["usuarioId"]),
        Index(value = ["usuarioId", "claveUnica"]),
        Index(value = ["estado"])
    ]
)
data class EntidadOperacionPendiente(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val usuarioId: Long,
    val tipoOperacion: String,
    val recursoId: String,
    val payload: String?,
    val claveUnica: String,
    val fechaCreacion: Long,
    val fechaUltimoIntento: Long?,
    val estado: String,
    val intentos: Int = 0,
    val ultimoMensajeError: String? = null
)

/**
 * Información de sincronización por recurso y usuario: fecha del último
 * intento, resultado y mensaje para poder informar en la interfaz.
 */
@Entity(
    tableName = "metadatos_sincronizacion",
    primaryKeys = ["usuarioId", "recurso"]
)
data class EntidadMetadatoSincronizacion(
    val usuarioId: Long,
    val recurso: String,
    val fechaUltimaSincronizacion: Long?,
    val estado: String?,
    val mensaje: String?
)
