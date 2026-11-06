package com.ejemplo.publicarproducto.data

import androidx.room.withTransaction
import com.ejemplo.publicarproducto.local.CLAVE_FILTRO_GLOBAL
import com.ejemplo.publicarproducto.local.EntidadBandeja
import com.ejemplo.publicarproducto.local.EntidadOperacionPendiente
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.local.TAMANO_PAGINA_BANDEJA
import com.ejemplo.publicarproducto.local.TiposOperacion
import com.ejemplo.publicarproducto.model.BandejaNotificacionesResponse
import com.ejemplo.publicarproducto.network.ProductoApi
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.network.ejecutarPeticion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Bandeja unificada de notificaciones (HU 53/54) con lectura Offline First.
 *
 * - La interfaz consume SIEMPRE la copia local de Room.
 * - Con internet, `refrescar` pide la página real al backend y actualiza Room;
 *   la interfaz se actualiza sola porque observa un `Flow`.
 * - Sin internet se sigue consultando Room con los últimos datos sincronizados.
 *
 * El backend es siempre la fuente oficial: cada sincronización sobrescribe la
 * copia local con la respuesta real del servidor.
 */
class NotificacionesRepositorio(
    private val bd: PulgaTecDatabase,
    private val api: ProductoApi,
    private val cola: ColaOperacionesRepositorio
) {

    /**
     * Página local de la bandeja. Replica los filtros del backend
     * (tipo, estado y rango de fechas) y su orden estable.
     */
    fun observarBandeja(
        usuarioId: Long,
        tipo: String?,
        estado: String?,
        desde: String?,
        hasta: String?,
        pagina: Int,
        tamano: Int = TAMANO_PAGINA_BANDEJA
    ): Flow<BandejaNotificacionesResponse> {
        val clave = claveFiltro(tipo, estado, desde, hasta, pagina)
        return combine(
            bd.notificacionDao().observarPagina(
                usuarioId = usuarioId,
                tipo = tipo,
                estado = estado,
                desde = Fechas.normalizarDesde(desde),
                hasta = Fechas.normalizarHasta(hasta),
                limite = tamano,
                desplazamiento = pagina * tamano
            ),
            bd.bandejaDao().observar(usuarioId, clave),
            bd.bandejaDao().observarPendientesGlobales(usuarioId)
        ) { avisos, metadatos, pendientesGlobales ->
            BandejaNotificacionesResponse(
                notificaciones = avisos.map { Mapeadores.aDto(it) },
                pagina = metadatos?.pagina ?: pagina,
                tamano = metadatos?.tamano ?: tamano,
                totalElementos = metadatos?.totalElementos ?: avisos.size.toLong(),
                totalPaginas = metadatos?.totalPaginas ?: 1,
                tieneSiguiente = metadatos?.tieneSiguiente ?: false,
                pendientes = pendientesGlobales ?: metadatos?.pendientes ?: 0L
            )
        }
    }

    /**
     * Descarga la página real del backend y actualiza la copia local.
     * Los avisos con lectura guardada solo en el dispositivo conservan ese
     * estado: el servidor aún no la confirmó.
     */
    suspend fun refrescar(
        usuarioId: Long,
        tipo: String?,
        estado: String?,
        desde: String?,
        hasta: String?,
        pagina: Int,
        tamano: Int = TAMANO_PAGINA_BANDEJA
    ): ResultadoApi<BandejaNotificacionesResponse> {
        val resultado = ejecutarPeticion { api.listarBandeja(tipo, estado, desde, hasta, pagina) }
        if (resultado is ResultadoApi.Exito) {
            val respuesta = resultado.valor
            val ahora = System.currentTimeMillis()
            bd.withTransaction {
                val existentes = bd.notificacionDao().listarTodas(usuarioId)
                val lecturasLocales = existentes
                    .filter { it.pendienteLecturaLocal }
                    .map { it.idNotificacion }
                    .toSet()
                val entidades = respuesta.notificaciones.map { aviso ->
                    Mapeadores.aEntidad(
                        dto = aviso,
                        usuarioId = usuarioId,
                        ahora = ahora,
                        pendienteLecturaLocal = aviso.idNotificacion in lecturasLocales
                    )
                }
                bd.notificacionDao().guardarTodas(entidades)
                bd.bandejaDao().guardar(
                    Mapeadores.aEntidadBandeja(
                        usuarioId = usuarioId,
                        claveFiltro = claveFiltro(tipo, estado, desde, hasta, pagina),
                        respuesta = respuesta,
                        ahora = ahora
                    )
                )
                bd.bandejaDao().guardar(
                    EntidadBandeja(
                        usuarioId = usuarioId,
                        claveFiltro = CLAVE_FILTRO_GLOBAL,
                        pagina = 0,
                        tamano = 0,
                        totalElementos = 0,
                        totalPaginas = 0,
                        tieneSiguiente = false,
                        pendientes = respuesta.pendientes,
                        fechaSincronizacion = ahora
                    )
                )
            }
        }
        return resultado
    }

    /**
     * Guarda la lectura de un aviso EN EL DISPOSITIVO y la encola para el
     * servidor. Hasta que el backend confirme, la interfaz solo puede decir
     * que está pendiente de sincronización.
     *
     * @return `true` si el aviso estaba pendiente (el contador baja en uno).
     */
    suspend fun registrarLecturaLocal(usuarioId: Long, idNotificacion: Long): Boolean =
        bd.withTransaction {
            val aviso = bd.notificacionDao().buscar(usuarioId, idNotificacion)
            val estabaPendiente = aviso?.estado == "PENDIENTE"
            bd.notificacionDao().marcarLecturaLocal(usuarioId, idNotificacion, Fechas.ahora())
            asegurarContadorGlobal(usuarioId)
            if (estabaPendiente) {
                val pendientes = bd.bandejaDao().buscarPendientesGlobales(usuarioId) ?: 0L
                bd.bandejaDao().actualizarPendientes(usuarioId, maxOf(0L, pendientes - 1))
            }
            estabaPendiente
        }

    /** Guarda la lectura masiva en el dispositivo y la encola para el servidor. */
    suspend fun registrarLecturaMasivaLocal(usuarioId: Long): Int = bd.withTransaction {
        val pendientes = bd.notificacionDao().contarPendientesAhora(usuarioId)
        bd.notificacionDao().marcarTodasLecturaLocal(usuarioId, Fechas.ahora())
        asegurarContadorGlobal(usuarioId)
        bd.bandejaDao().actualizarPendientes(usuarioId, 0L)
        pendientes.toInt()
    }

    /** Encola la lectura individual de un aviso (sin duplicar la cola). */
    suspend fun encolarLectura(usuarioId: Long, idNotificacion: Long): EntidadOperacionPendiente =
        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.MARCAR_NOTIFICACION_LEIDA,
            recursoId = idNotificacion.toString(),
            payload = null,
            claveUnica = "notificacion:$idNotificacion"
        )

    /** Encola la lectura masiva de los avisos del usuario. */
    suspend fun encolarLecturaMasiva(usuarioId: Long): EntidadOperacionPendiente =
        cola.encolar(
            usuarioId = usuarioId,
            tipoOperacion = TiposOperacion.MARCAR_TODAS_NOTIFICACIONES_LEIDAS,
            recursoId = "todas",
            payload = null,
            claveUnica = "notificaciones:todas:$usuarioId"
        )

    /** Aplica el estado que confirmó el servidor (el backend siempre gana). */
    suspend fun aplicarEstadoServidor(
        usuarioId: Long,
        idNotificacion: Long,
        estado: String?,
        fechaLectura: String?,
        pendientes: Long?
    ) = bd.withTransaction {
        bd.notificacionDao().aplicarEstadoServidor(
            usuarioId = usuarioId,
            idNotificacion = idNotificacion,
            estado = estado,
            fechaLectura = Fechas.normalizar(fechaLectura)
        )
        if (pendientes != null) {
            asegurarContadorGlobal(usuarioId)
            bd.bandejaDao().actualizarPendientes(usuarioId, pendientes)
        }
    }

    /** Confirma la lectura masiva devuelta por el servidor. */
    suspend fun aplicarEstadoServidorGlobal(usuarioId: Long, pendientes: Long?) =
        bd.withTransaction {
            bd.notificacionDao().aplicarEstadoServidorGlobal(usuarioId, Fechas.ahora())
            if (pendientes != null) {
                asegurarContadorGlobal(usuarioId)
                bd.bandejaDao().actualizarPendientes(usuarioId, pendientes)
            }
        }

    /** El servidor rechazó la lectura: el aviso vuelve a estar pendiente. */
    suspend fun revertirLectura(usuarioId: Long, idNotificacion: Long) = bd.withTransaction {
        bd.notificacionDao().revertirLecturaLocal(usuarioId, idNotificacion)
        asegurarContadorGlobal(usuarioId)
        val pendientes = bd.notificacionDao().contarPendientesAhora(usuarioId)
        bd.bandejaDao().actualizarPendientes(usuarioId, pendientes)
    }

    /** El servidor rechazó la lectura masiva: los avisos vuelven a estar pendientes. */
    suspend fun revertirLecturas(usuarioId: Long) = bd.withTransaction {
        bd.notificacionDao().revertirLecturasLocales(usuarioId)
        asegurarContadorGlobal(usuarioId)
        val pendientes = bd.notificacionDao().contarPendientesAhora(usuarioId)
        bd.bandejaDao().actualizarPendientes(usuarioId, pendientes)
    }

    /** Fija el contador global de pendientes (valor oficial del servidor). */
    suspend fun actualizarContadorPendientes(usuarioId: Long, pendientes: Long) = bd.withTransaction {
        asegurarContadorGlobal(usuarioId)
        bd.bandejaDao().actualizarPendientes(usuarioId, pendientes)
    }

    /** Borra toda la información local de un usuario (cierre de sesión). */
    suspend fun borrarUsuario(usuarioId: Long) = bd.withTransaction {
        bd.notificacionDao().borrarUsuario(usuarioId)
        bd.bandejaDao().borrarUsuario(usuarioId)
    }

    /** Crea la fila global del contador si todavía no existe. */
    private suspend fun asegurarContadorGlobal(usuarioId: Long) {
        if (bd.bandejaDao().buscarPendientesGlobales(usuarioId) == null) {
            val pendientes = bd.notificacionDao().contarPendientesAhora(usuarioId)
            bd.bandejaDao().guardar(
                EntidadBandeja(
                    usuarioId = usuarioId,
                    claveFiltro = CLAVE_FILTRO_GLOBAL,
                    pagina = 0,
                    tamano = 0,
                    totalElementos = 0,
                    totalPaginas = 0,
                    tieneSiguiente = false,
                    pendientes = pendientes,
                    fechaSincronizacion = System.currentTimeMillis()
                )
            )
        }
    }

    companion object {
        /** Clave de los metadatos de una combinación de filtros y página. */
        fun claveFiltro(
            tipo: String?,
            estado: String?,
            desde: String?,
            hasta: String?,
            pagina: Int
        ): String = "$tipo|$estado|$desde|$hasta|$pagina"
    }
}
