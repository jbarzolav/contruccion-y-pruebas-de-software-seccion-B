package com.ejemplo.publicarproducto.data

import com.ejemplo.publicarproducto.local.EntidadOperacionPendiente
import com.ejemplo.publicarproducto.local.EstadosOperacion
import com.ejemplo.publicarproducto.local.OperacionPendienteDao
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

/**
 * Cola persistente de operaciones pendientes.
 *
 * Cada operación registra:
 * - id local (`id`, autogenerado por Room),
 * - tipo (`tipoOperacion`),
 * - id del recurso afectado (`recursoId`),
 * - datos para enviar (`payload`),
 * - clave única de deduplicación (`claveUnica`),
 * - fecha de creación (`fechaCreacion`),
 * - estado de sincronización (`estado`),
 * - número de intentos (`intentos`) y último error.
 *
 * La cola vive en Room: no se pierde al cerrar la aplicación.
 */
class ColaOperacionesRepositorio(private val dao: OperacionPendienteDao) {

    private val gson = Gson()

    /**
     * Encola una operación. Si ya existe una operación ACTIVA con la misma
     * clave se devuelve la existente: nunca se duplica la misma operación
     * (protección frente a reintentos y toques repetidos).
     */
    suspend fun encolar(
        usuarioId: Long,
        tipoOperacion: String,
        recursoId: String,
        payload: Any? = null,
        claveUnica: String,
        ahora: Long = System.currentTimeMillis()
    ): EntidadOperacionPendiente {
        val existente = dao.buscarActivaPorClave(usuarioId, claveUnica)
        if (existente != null) return existente
        val operacion = EntidadOperacionPendiente(
            usuarioId = usuarioId,
            tipoOperacion = tipoOperacion,
            recursoId = recursoId,
            payload = payload?.let { gson.toJson(it) },
            claveUnica = claveUnica,
            fechaCreacion = ahora,
            fechaUltimoIntento = null,
            estado = EstadosOperacion.PENDIENTE,
            intentos = 0,
            ultimoMensajeError = null
        )
        val id = dao.insertar(operacion)
        return operacion.copy(id = id)
    }

    /** Operaciones que todavía esperan confirmación del servidor. */
    fun observarPendientes(usuarioId: Long): Flow<List<EntidadOperacionPendiente>> =
        dao.observarPendientes(usuarioId)

    /** Número de operaciones que todavía esperan confirmación del servidor. */
    fun contarPendientes(usuarioId: Long): Flow<Long> = dao.contarPendientes(usuarioId)

    /** Historial completo de operaciones del usuario (confirmadas y rechazadas). */
    fun observarTodas(usuarioId: Long): Flow<List<EntidadOperacionPendiente>> =
        dao.observarTodas(usuarioId)

    /** Operaciones pendientes en orden de creación (el orden de la cola). */
    suspend fun listarPendientes(usuarioId: Long): List<EntidadOperacionPendiente> =
        dao.listarPendientes(usuarioId)

    /** Marca la operación como en curso y cuenta el intento. */
    suspend fun marcarEnCurso(id: Long, ahora: Long = System.currentTimeMillis()) {
        dao.registrarIntento(id, EstadosOperacion.EN_CURSO, ahora, null)
    }

    /**
     * Registra el resultado de un intento fallido con error temporal: la
     * operación sigue pendiente. El contador de intentos ya quedó contabilizado
     * contabilizó al marcar la operación en curso.
     */
    suspend fun registrarIntento(id: Long, mensaje: String, ahora: Long = System.currentTimeMillis()) {
        dao.actualizarIntento(id, EstadosOperacion.PENDIENTE, ahora, mensaje)
    }

    /** El servidor confirmó la operación. */
    suspend fun confirmar(id: Long, mensaje: String? = null) {
        dao.actualizarEstado(id, EstadosOperacion.CONFIRMADA, mensaje)
    }

    /** El servidor rechazó la operación: no se reintenta más. */
    suspend fun rechazar(id: Long, mensaje: String) {
        dao.actualizarEstado(id, EstadosOperacion.RECHAZADA, mensaje)
    }

    /** La sesión expiró: la operación espera a que el usuario vuelva a entrar. */
    suspend fun requerirSesion(id: Long, mensaje: String) {
        dao.actualizarEstado(id, EstadosOperacion.REQUIERE_SESION, mensaje)
    }

    /** Elimina las operaciones ya confirmadas (no se pierden las pendientes). */
    suspend fun borrarConfirmadas(usuarioId: Long) = dao.borrarConfirmadas(usuarioId)

    /** Borra toda la cola del usuario (cierre de sesión). */
    suspend fun borrarUsuario(usuarioId: Long) = dao.borrarUsuario(usuarioId)

    /** Lee el `payload` JSON de una operación. */
    fun <T> leerPayload(operacion: EntidadOperacionPendiente, tipo: Class<T>): T? = runCatching {
        val texto = operacion.payload ?: return null
        gson.fromJson(texto, tipo)
    }.getOrNull()

    /** Lee el `payload` JSON de una operación en un mapa simple. */
    fun leerPayload(operacion: EntidadOperacionPendiente): Map<String, String> = runCatching {
        val texto = operacion.payload ?: return emptyMap()
        @Suppress("UNCHECKED_CAST")
        val mapa = gson.fromJson(texto, Map::class.java) as Map<String, String>
        mapa
    }.getOrDefault(emptyMap())
}
