package com.ejemplo.publicarproducto.sync

import com.ejemplo.publicarproducto.data.ColaOperacionesRepositorio
import com.ejemplo.publicarproducto.data.NotificacionesRepositorio
import com.ejemplo.publicarproducto.data.PedidosRepositorio
import com.ejemplo.publicarproducto.local.EntidadMetadatoSincronizacion
import com.ejemplo.publicarproducto.local.EntidadOperacionPendiente
import com.ejemplo.publicarproducto.local.EstadosOperacion
import com.ejemplo.publicarproducto.local.EstadosSincronizacion
import com.ejemplo.publicarproducto.local.MAX_INTENTOS_OPERACION
import com.ejemplo.publicarproducto.local.MetadatoSincronizacionDao
import com.ejemplo.publicarproducto.local.PulgaTecDatabase
import com.ejemplo.publicarproducto.local.RecursosSincronizacion
import com.ejemplo.publicarproducto.local.TiposOperacion
import com.ejemplo.publicarproducto.model.EstadoPedidoRequest
import com.ejemplo.publicarproducto.network.ProductoApi
import com.ejemplo.publicarproducto.network.ResultadoApi
import com.ejemplo.publicarproducto.network.ejecutarPeticion
import com.ejemplo.publicarproducto.session.SessionManager

/** Resultado de una pasada de sincronización. */
sealed class ResultadoSincronizacion {
    /** Todo sincronizado correctamente (puede no haber nada pendiente). */
    data object Exito : ResultadoSincronizacion()

    /** Error temporal (sin red o servidor caído): hay que reintentar más tarde. */
    data class Reintentar(val mensaje: String) : ResultadoSincronizacion()

    /** La sesión expiró: la sincronización se detiene hasta que el usuario entre. */
    data class SesionExpirada(val mensaje: String) : ResultadoSincronizacion()
}

/** Resultado interno del procesamiento de una operación. */
private sealed class ResultadoOperacion {
    data object Confirmada : ResultadoOperacion()
    data class Rechazada(val mensaje: String) : ResultadoOperacion()
    data class Temporal(val mensaje: String) : ResultadoOperacion()
    data class SesionExpirada(val mensaje: String) : ResultadoOperacion()
}

/**
 * Sincronizador de la cola pendiente.
 *
 * Reglas:
 * - las operaciones se procesan EN ORDEN de creación;
 * - se usa el cliente Retrofit existente (`UserApiClient.productoApi`) con el
 *   Bearer de la sesión vigente; no se crea una segunda implementación;
 * - un error temporal deja la operación pendiente y pide reintento;
 * - un 4xx marca la operación como RECHAZADA (no se reintenta nunca);
 * - un 401 detiene la sincronización y espera a que el usuario vuelva a entrar;
 * - al confirmar, la copia local de Room se actualiza con la respuesta real
 *   del servidor.
 */
class ServicioSincronizacion(
    private val bd: PulgaTecDatabase,
    private val api: ProductoApi,
    private val cola: ColaOperacionesRepositorio,
    private val notificaciones: NotificacionesRepositorio,
    private val pedidos: PedidosRepositorio,
    private val metadatos: MetadatoSincronizacionDao,
    private val sesion: SessionManager
) {

    /** Número máximo de vueltas del sincronizador en una misma ejecución. */
    private val maximoVueltas = 5

    suspend fun sincronizar(usuarioId: Long): ResultadoSincronizacion {
        if (!sesion.haySesion()) {
            registrar(usuarioId, EstadosSincronizacion.SESION_EXPIRADA, "No hay sesión activa.")
            return ResultadoSincronizacion.SesionExpirada("No hay sesión activa.")
        }

        var confirmadas = 0
        var rechazadas = 0
        var ultimoRechazo: String? = null

        var vuelta = 0
        while (vuelta < maximoVueltas) {
            vuelta++
            val pendientes = cola.listarPendientes(usuarioId)
            if (pendientes.isEmpty()) break

            var temporal: String? = null
            for (operacion in pendientes) {
                cola.marcarEnCurso(operacion.id)
                val resultado = when (operacion.tipoOperacion) {
                    TiposOperacion.MARCAR_NOTIFICACION_LEIDA ->
                        sincronizarLectura(usuarioId, operacion)
                    TiposOperacion.MARCAR_TODAS_NOTIFICACIONES_LEIDAS ->
                        sincronizarLecturaMasiva(usuarioId, operacion)
                    TiposOperacion.ACTUALIZAR_ESTADO_PEDIDO ->
                        sincronizarEstadoPedido(usuarioId, operacion)
                    else -> ResultadoOperacion.Rechazada(
                        "Tipo de operación desconocido: ${operacion.tipoOperacion}."
                    )
                }
                when (resultado) {
                    is ResultadoOperacion.Confirmada -> confirmadas++
                    is ResultadoOperacion.Rechazada -> {
                        rechazadas++
                        ultimoRechazo = resultado.mensaje
                    }
                    is ResultadoOperacion.Temporal -> {
                        temporal = resultado.mensaje
                        break
                    }
                    is ResultadoOperacion.SesionExpirada -> {
                        registrar(
                            usuarioId,
                            EstadosSincronizacion.SESION_EXPIRADA,
                            resultado.mensaje
                        )
                        return ResultadoSincronizacion.SesionExpirada(resultado.mensaje)
                    }
                }
            }

            if (temporal != null) {
                registrar(usuarioId, EstadosSincronizacion.SIN_CONEXION, temporal)
                return ResultadoSincronizacion.Reintentar(temporal)
            }
        }

        // El backend tiene prioridad: la copia local se refresca con la
        // respuesta real del servidor.
        val refrescoBandeja = notificaciones.refrescar(usuarioId, null, null, null, null, 0)
        val refrescoVentas = pedidos.refrescarVentas(usuarioId)
        val refrescoCompras = pedidos.refrescarCompras(usuarioId)

        val falloTemporal = listOf(refrescoBandeja, refrescoVentas, refrescoCompras)
            .filterIsInstance<ResultadoApi.Error>()
            .firstOrNull { it.temporal || it.sesionExpirada }

        if (falloTemporal != null) {
            if (falloTemporal.sesionExpirada) {
                registrar(usuarioId, EstadosSincronizacion.SESION_EXPIRADA, falloTemporal.mensaje)
                return ResultadoSincronizacion.SesionExpirada(falloTemporal.mensaje)
            }
            registrar(usuarioId, EstadosSincronizacion.SIN_CONEXION, falloTemporal.mensaje)
            return ResultadoSincronizacion.Reintentar(falloTemporal.mensaje)
        }

        val mensaje = when {
            rechazadas > 0 -> "El servidor rechazó ${rechazadas} operación(es). ${ultimoRechazo.orEmpty()}".trim()
            confirmadas > 0 -> "Se sincronizaron $confirmadas operación(es) con el servidor."
            else -> "La información local está al día con el servidor."
        }
        registrar(
            usuarioId,
            if (rechazadas > 0) EstadosSincronizacion.RECHAZADO else EstadosSincronizacion.SINCRONIZADO,
            mensaje
        )
        return ResultadoSincronizacion.Exito
    }

    /** HU 54 - Lectura individual de un aviso propio (operación idempotente). */
    private suspend fun sincronizarLectura(
        usuarioId: Long,
        operacion: EntidadOperacionPendiente
    ): ResultadoOperacion {
        val idNotificacion = operacion.recursoId.toLongOrNull()
            ?: return ResultadoOperacion.Rechazada("Identificador de notificación inválido.")
        return when (val resultado = ejecutarPeticion { api.marcarNotificacionLeida(idNotificacion) }) {
            is ResultadoApi.Exito -> {
                val cuerpo = resultado.valor
                notificaciones.aplicarEstadoServidor(
                    usuarioId = usuarioId,
                    idNotificacion = idNotificacion,
                    estado = cuerpo.estado,
                    fechaLectura = cuerpo.fechaLectura,
                    pendientes = cuerpo.pendientes
                )
                cola.confirmar(operacion.id)
                ResultadoOperacion.Confirmada
            }
            is ResultadoApi.Error -> when {
                resultado.sesionExpirada -> {
                    cola.requerirSesion(operacion.id, resultado.mensaje)
                    ResultadoOperacion.SesionExpirada(resultado.mensaje)
                }
                resultado.temporal -> {
                    if (operacion.intentos + 1 >= MAX_INTENTOS_OPERACION) {
                        cola.rechazar(
                            operacion.id,
                            "Se agotaron los reintentos: ${resultado.mensaje}"
                        )
                        notificaciones.revertirLectura(usuarioId, idNotificacion)
                        ResultadoOperacion.Rechazada(resultado.mensaje)
                    } else {
                        cola.registrarIntento(operacion.id, resultado.mensaje)
                        ResultadoOperacion.Temporal(resultado.mensaje)
                    }
                }
                else -> {
                    cola.rechazar(operacion.id, resultado.mensaje)
                    notificaciones.revertirLectura(usuarioId, idNotificacion)
                    ResultadoOperacion.Rechazada(resultado.mensaje)
                }
            }
        }
    }

    /** HU 54 - Lectura masiva de todos los avisos propios (operación idempotente). */
    private suspend fun sincronizarLecturaMasiva(
        usuarioId: Long,
        operacion: EntidadOperacionPendiente
    ): ResultadoOperacion = when (
        val resultado = ejecutarPeticion { api.marcarTodasComoLeidas() }
    ) {
        is ResultadoApi.Exito -> {
            notificaciones.aplicarEstadoServidorGlobal(usuarioId, resultado.valor.pendientes)
            cola.confirmar(operacion.id)
            ResultadoOperacion.Confirmada
        }
        is ResultadoApi.Error -> when {
            resultado.sesionExpirada -> {
                cola.requerirSesion(operacion.id, resultado.mensaje)
                ResultadoOperacion.SesionExpirada(resultado.mensaje)
            }
            resultado.temporal -> {
                if (operacion.intentos + 1 >= MAX_INTENTOS_OPERACION) {
                    cola.rechazar(
                        operacion.id,
                        "Se agotaron los reintentos: ${resultado.mensaje}"
                    )
                    notificaciones.revertirLecturas(usuarioId)
                    ResultadoOperacion.Rechazada(resultado.mensaje)
                } else {
                    cola.registrarIntento(operacion.id, resultado.mensaje)
                    ResultadoOperacion.Temporal(resultado.mensaje)
                }
            }
            else -> {
                cola.rechazar(operacion.id, resultado.mensaje)
                notificaciones.revertirLecturas(usuarioId)
                ResultadoOperacion.Rechazada(resultado.mensaje)
            }
        }
    }

    /**
     * HU 49 - Cambio de estado de un pedido como vendedor.
     *
     * El endpoint NO es idempotente (repetir el mismo destino responde 400
     * "El pedido ya se encuentra en estado X"), así que antes de reintentar
     * se comprueba el mensaje del servidor: si el estado ya era el destino,
     * la operación se da por confirmada en lugar de rechazada.
     */
    private suspend fun sincronizarEstadoPedido(
        usuarioId: Long,
        operacion: EntidadOperacionPendiente
    ): ResultadoOperacion {
        val idPedido = operacion.recursoId.toLongOrNull()
            ?: return ResultadoOperacion.Rechazada("Identificador de pedido inválido.")
        val payload = cola.leerPayload(operacion)
        val estado = payload["estado"]
            ?: return ResultadoOperacion.Rechazada("La operación no indica el estado de destino.")
        val ambito = payload["ambito"] ?: com.ejemplo.publicarproducto.local.Ambitos.VENTAS

        return when (val resultado = ejecutarPeticion { api.actualizarEstadoPedido(idPedido, EstadoPedidoRequest(estado)) }) {
            is ResultadoApi.Exito -> {
                val cuerpo = resultado.valor
                pedidos.aplicarEstadoServidor(
                    usuarioId = usuarioId,
                    idPedido = idPedido,
                    ambito = ambito,
                    estado = cuerpo.estado,
                    fechaActualizacion = cuerpo.fechaActualizacion,
                    estadosPermitidos = cuerpo.estadosPermitidos?.joinToString(",")
                )
                cola.confirmar(operacion.id)
                ResultadoOperacion.Confirmada
            }
            is ResultadoApi.Error -> when {
                resultado.sesionExpirada -> {
                    cola.requerirSesion(operacion.id, resultado.mensaje)
                    ResultadoOperacion.SesionExpirada(resultado.mensaje)
                }
                resultado.temporal -> {
                    if (operacion.intentos + 1 >= MAX_INTENTOS_OPERACION) {
                        cola.rechazar(
                            operacion.id,
                            "Se agotaron los reintentos: ${resultado.mensaje}"
                        )
                        pedidos.limpiarEstadoPendiente(usuarioId, idPedido, ambito)
                        ResultadoOperacion.Rechazada(resultado.mensaje)
                    } else {
                        cola.registrarIntento(operacion.id, resultado.mensaje)
                        ResultadoOperacion.Temporal(resultado.mensaje)
                    }
                }
                yaAplicado(resultado.mensaje, estado) -> {
                    // El servidor ya tenía el estado pedido: el reintento es seguro.
                    pedidos.aplicarEstadoServidor(
                        usuarioId = usuarioId,
                        idPedido = idPedido,
                        ambito = ambito,
                        estado = estado,
                        fechaActualizacion = null,
                        estadosPermitidos = null
                    )
                    cola.confirmar(operacion.id, "El estado ya estaba aplicado en el servidor.")
                    ResultadoOperacion.Confirmada
                }
                else -> {
                    cola.rechazar(operacion.id, resultado.mensaje)
                    pedidos.limpiarEstadoPendiente(usuarioId, idPedido, ambito)
                    ResultadoOperacion.Rechazada(resultado.mensaje)
                }
            }
        }
    }

    /**
     * El backend responde 400 "El pedido ya se encuentra en estado X" cuando
     * la transición ya se aplicó: en ese caso la operación se confirma.
     */
    private fun yaAplicado(mensaje: String, estadoDestino: String): Boolean =
        mensaje.contains("ya se encuentra en estado", ignoreCase = true) &&
            mensaje.contains(estadoDestino, ignoreCase = true)

    /** Guarda el resultado de la sincronización para poder informarlo en la UI. */
    private suspend fun registrar(usuarioId: Long, estado: String, mensaje: String) {
        metadatos.guardar(
            EntidadMetadatoSincronizacion(
                usuarioId = usuarioId,
                recurso = RecursosSincronizacion.OPERACIONES,
                fechaUltimaSincronizacion = System.currentTimeMillis(),
                estado = estado,
                mensaje = mensaje
            )
        )
    }

    companion object {
        /** Estados en los que una operación ya no espera confirmación. */
        val ESTADOS_TERMINALES = EstadosOperacion.TERMINALES
    }
}
