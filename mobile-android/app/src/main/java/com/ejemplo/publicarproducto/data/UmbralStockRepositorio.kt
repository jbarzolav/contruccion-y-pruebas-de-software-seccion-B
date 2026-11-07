package com.ejemplo.publicarproducto.data

import androidx.room.withTransaction
import com.ejemplo.publicarproducto.local.EntidadOperacionPendiente
import com.ejemplo.publicarproducto.local.EntidadUmbralStock
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.local.TiposOperacion
import com.ejemplo.publicarproducto.model.AvisoStockBajoResponse
import com.ejemplo.publicarproducto.model.UmbralStockResponse
import com.ejemplo.publicarproducto.network.ProductoApi
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.network.ejecutarPeticion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * HU 58 - Umbrales de stock y avisos de stock bajo del vendedor.
 *
 * - El panel se observa desde la copia local de Room.
 * - Los avisos de stock bajo se leen del SISTEMA DE NOTIFICACIONES EXISTENTE
 *   (tipo STOCK_BAJO): no hay una segunda bandeja ni una segunda cola.
 * - El cambio de umbral se guarda en el dispositivo y se encola en la cola
 *   persistente existente: el PUT es idempotente, así que reintentar es
 *   seguro. Hasta que el servidor confirme, la interfaz solo dice
 *   "pendiente de sincronizar".
 */
class UmbralStockRepositorio(
    private val bd: PulgaTecDatabase,
    private val api: ProductoApi,
    private val cola: ColaOperacionesRepositorio,
    private val notificaciones: NotificacionesRepositorio
) {

    /** Panel de publicaciones propias con su umbral y estado de stock. */
    fun observarUmbrales(usuarioId: Long): Flow<List<UmbralStockResponse>> =
        bd.umbralStockDao().observar(usuarioId).map { lista -> lista.map { aDto(it) } }

    /**
     * Avisos de stock bajo: se combinan los avisos del sistema de
     * notificaciones existente con el umbral vigente de cada publicación.
     */
    fun observarAvisos(usuarioId: Long): Flow<List<AvisoStockBajoResponse>> = combine(
        bd.notificacionDao().observarStockBajo(usuarioId),
        bd.umbralStockDao().observar(usuarioId)
    ) { avisos, umbrales ->
        val porProducto = umbrales.associateBy { it.idProducto }
        avisos.mapNotNull { aviso ->
            val idProducto = aviso.productoId ?: return@mapNotNull null
            val registro = porProducto[idProducto]
            AvisoStockBajoResponse(
                idNotificacion = aviso.idNotificacion,
                idProducto = idProducto,
                nombre = registro?.nombre,
                imagenUrl = registro?.imagenUrl,
                stockActual = registro?.stock,
                umbral = registro?.umbral,
                stockBajo = registro?.stockBajo ?: false,
                estado = aviso.estado,
                fecha = aviso.fecha
            )
        }
    }

    /** Umbrales pendientes de confirmar por el servidor, por publicación. */
    fun observarPendientes(usuarioId: Long): Flow<Map<Long, Int>> =
        bd.umbralStockDao().observar(usuarioId).map { lista ->
            lista.mapNotNull { registro ->
                registro.umbralPendienteLocal?.let { registro.idProducto to it }
            }.toMap()
        }

    /** Descarga el panel real del backend y actualiza la copia local. */
    suspend fun refrescarUmbrales(usuarioId: Long): ResultadoApi<List<UmbralStockResponse>> {
        val resultado = ejecutarPeticion { api.listarUmbralesStock() }
        if (resultado is ResultadoApi.Exito) {
            val ahora = System.currentTimeMillis()
            bd.withTransaction {
                val pendientes = bd.umbralStockDao().listar(usuarioId)
                    .mapNotNull { registro -> registro.umbralPendienteLocal?.let { registro.idProducto to it } }
                    .toMap()
                val entidades = resultado.valor.mapNotNull { dto ->
                    val idProducto = dto.idProducto ?: return@mapNotNull null
                    EntidadUmbralStock(
                        usuarioId = usuarioId,
                        idProducto = idProducto,
                        nombre = dto.nombre,
                        imagenUrl = dto.imagenUrl,
                        stock = dto.stock,
                        umbral = dto.umbral,
                        stockBajo = dto.stockBajo ?: false,
                        alertaNotificada = dto.alertaNotificada ?: false,
                        fechaActualizacion = Fechas.normalizar(dto.fechaActualizacion),
                        // El umbral propuesto en el dispositivo se conserva
                        // hasta que el servidor confirme el cambio.
                        umbralPendienteLocal = pendientes[idProducto],
                        fechaSincronizacion = ahora
                    )
                }
                bd.umbralStockDao().guardar(entidades)
            }
        }
        return resultado
    }

    /**
     * Actualiza los avisos de stock bajo usando la bandeja EXISTENTE
     * (filtro por tipo STOCK_BAJO). No se crea una bandeja paralela.
     */
    suspend fun refrescarAvisos(
        usuarioId: Long
    ): ResultadoApi<com.ejemplo.publicarproducto.model.BandejaNotificacionesResponse> =
        notificaciones.refrescar(usuarioId, TIPO_STOCK_BAJO, null, null, null, 0)

    /**
     * Guarda en el dispositivo el umbral propuesto por el usuario.
     * NO pisa el umbral oficial del servidor: ese siempre tiene prioridad.
     */
    suspend fun registrarUmbralPendienteLocal(
        usuarioId: Long,
        idProducto: Long,
        umbral: Int
    ) = bd.umbralStockDao().marcarUmbralPendienteLocal(usuarioId, idProducto, umbral)

    /** Encola el cambio de umbral para sincronizarlo con el backend. */
    suspend fun encolarUmbral(
        usuarioId: Long,
        idProducto: Long,
        umbral: Int
    ): EntidadOperacionPendiente = cola.encolar(
        usuarioId = usuarioId,
        tipoOperacion = TiposOperacion.ACTUALIZAR_UMBRAL_STOCK,
        recursoId = idProducto.toString(),
        payload = mapOf("umbral" to umbral.toString()),
        claveUnica = "umbral:$idProducto:$umbral"
    )

    /** Aplica el umbral que confirmó el servidor y descarta la propuesta local. */
    suspend fun aplicarUmbralServidor(
        usuarioId: Long,
        cuerpo: UmbralStockResponse
    ) {
        val idProducto = cuerpo.idProducto ?: return
        bd.withTransaction {
            val existente = bd.umbralStockDao().buscar(usuarioId, idProducto)
            bd.umbralStockDao().guardarUno(
                EntidadUmbralStock(
                    usuarioId = usuarioId,
                    idProducto = idProducto,
                    nombre = cuerpo.nombre ?: existente?.nombre,
                    imagenUrl = cuerpo.imagenUrl ?: existente?.imagenUrl,
                    stock = cuerpo.stock ?: existente?.stock,
                    umbral = cuerpo.umbral,
                    stockBajo = cuerpo.stockBajo ?: false,
                    alertaNotificada = cuerpo.alertaNotificada ?: false,
                    fechaActualizacion = Fechas.normalizar(cuerpo.fechaActualizacion),
                    umbralPendienteLocal = null,
                    fechaSincronizacion = System.currentTimeMillis()
                )
            )
        }
    }

    /** El servidor rechazó el cambio: se descarta la propuesta local. */
    suspend fun limpiarUmbralPendiente(usuarioId: Long, idProducto: Long) =
        bd.umbralStockDao().limpiarUmbralPendienteLocal(usuarioId, idProducto)

    /** Borra toda la información local de umbrales del usuario (cierre de sesión). */
    suspend fun borrarUsuario(usuarioId: Long) = bd.umbralStockDao().borrarUsuario(usuarioId)

    private fun aDto(registro: EntidadUmbralStock): UmbralStockResponse = UmbralStockResponse(
        idProducto = registro.idProducto,
        nombre = registro.nombre,
        imagenUrl = registro.imagenUrl,
        stock = registro.stock,
        umbral = registro.umbral,
        stockBajo = registro.stockBajo,
        alertaNotificada = registro.alertaNotificada,
        fechaActualizacion = registro.fechaActualizacion
    )

    companion object {
        /** Tipo de aviso de stock bajo en el sistema de notificaciones. */
        const val TIPO_STOCK_BAJO = "STOCK_BAJO"
    }
}
