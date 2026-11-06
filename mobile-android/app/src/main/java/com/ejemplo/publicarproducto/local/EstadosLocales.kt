package com.ejemplo.publicarproducto.local

/**
 * Catálogos de la capa local (Offline First).
 *
 * Se centralizan aquí para que la cola, el sincronizador y la interfaz
 * compartan exactamente los mismos estados.
 */

/** Tipos de operación que la cola persistente sabe sincronizar. */
object TiposOperacion {
    /** Marcar una notificación propia como leída (HU 54). */
    const val MARCAR_NOTIFICACION_LEIDA = "MARCAR_NOTIFICACION_LEIDA"

    /** Marcar todas las notificaciones propias como leídas (HU 54). */
    const val MARCAR_TODAS_NOTIFICACIONES_LEIDAS = "MARCAR_TODAS_NOTIFICACIONES_LEIDAS"

    /** Actualizar el estado de un pedido (HU 49). */
    const val ACTUALIZAR_ESTADO_PEDIDO = "ACTUALIZAR_ESTADO_PEDIDO"
}

/** Estados de una operación de la cola. */
object EstadosOperacion {
    /** En cola: todavía no se envió al servidor. */
    const val PENDIENTE = "PENDIENTE"

    /** Enviándose en este momento. */
    const val EN_CURSO = "EN_CURSO"

    /** El servidor confirmó la operación. */
    const val CONFIRMADA = "CONFIRMADA"

    /** El servidor rechazó la operación: no se reintenta. */
    const val RECHAZADA = "RECHAZADA"

    /** La sesión expiró: la operación espera a que el usuario vuelva a entrar. */
    const val REQUIERE_SESION = "REQUIERE_SESION"

    /** Estados en los que la operación todavía espera confirmación del servidor. */
    val ACTIVOS = setOf(PENDIENTE, EN_CURSO, REQUIERE_SESION)

    /** Estados en los que la operación ya no se reintentará. */
    val TERMINALES = setOf(CONFIRMADA, RECHAZADA)
}

/** Resultado de la última sincronización, para poder informarlo en la interfaz. */
object EstadosSincronizacion {
    const val SINCRONIZADO = "SINCRONIZADO"
    const val RECHAZADO = "RECHAZADO"
    const val SIN_CONEXION = "SIN_CONEXION"
    const val SESION_EXPIRADA = "SESION_EXPIRADA"
    const val PENDIENTE = "PENDIENTE"
}

/** Recursos donde se registra la información de sincronización. */
object RecursosSincronizacion {
    const val BANDEJA = "BANDEJA"
    const val PEDIDOS = "PEDIDOS"
    const val OPERACIONES = "OPERACIONES"
}

/** Clave reservada de la fila de metadatos con el contador global de pendientes. */
const val CLAVE_FILTRO_GLOBAL = "GLOBAL"

/**
 * Tamaño de página por defecto de la bandeja unificada.
 * El backend usa 20 por defecto y admite hasta 50.
 */
const val TAMANO_PAGINA_BANDEJA = 20

/**
 * Número máximo de intentos de una operación con error temporal.
 * Al superarlo la operación se marca como RECHAZADA para no reintentar
 * indefinidamente.
 */
const val MAX_INTENTOS_OPERACION = 10
