package com.ejemplo.publicarproducto.model.remote

/**
 * R4 - Política de notificación de los errores de autorización.
 *
 * La regla decide, a partir de la respuesta HTTP, qué código debe
 * notificarse al bus de sesión. Vive aparte del interceptor de OkHttp para
 * que sea una función pura: así se puede verificar en la JVM toda la matriz
 * de casos (token presente, token ausente, ruta pública, 403...) sin
 * levantar un servidor.
 *
 * Reglas:
 * - Las rutas públicas (login, recuperación y restablecimiento) NUNCA
 *   notifican una expiración de sesión: un 401 del login significa
 *   "correo o contraseña incorrectos", no "sesión caducada".
 * - Un 401 se notifica cuando la petición se hizo con la misma sesión que
 *   sigue vigente, y también cuando NO había token (R4): el backend
 *   rechazó la petición y la interfaz debe volver al login.
 * - Un 403 solo se considera cuando la sesión es la misma que envió la
 *   petición, y el interceptor confirma que el cuerpo dice "Autorización".
 */
internal object PoliticaErroresAutorizacion {

    /** Rutas públicas: su 401 no es una expiración de sesión. */
    private val RUTAS_PUBLICAS = setOf(
        "/api/auth/login",
        "/api/auth/recuperacion",
        "/api/auth/recuperacion/verificar",
        "/api/auth/restablecer"
    )

    /** ¿La ruta es pública (sin sesión obligatoria)? */
    fun esRutaPublica(ruta: String): Boolean = ruta in RUTAS_PUBLICAS

    /**
     * Código que hay que notificar por la respuesta recibida.
     *
     * @param ruta ruta codificada de la petición.
     * @param tokenEnviado token que se envió con la petición (`null` si no había).
     * @param tokenVigente token de la sesión en el momento de la respuesta.
     * @param codigo código HTTP de la respuesta.
     * @return `401`, `403` o `null` si no hay que notificar nada.
     */
    fun codigoANotificar(
        ruta: String,
        tokenEnviado: String?,
        tokenVigente: String?,
        codigo: Int
    ): Int? {
        if (esRutaPublica(ruta)) return null
        val mismaSesion = tokenEnviado != null && tokenEnviado == tokenVigente
        // R4 - Sin token no hay sesión que pueda haber caducado: el 401 se
        // gestiona igual, porque el backend rechazó la petición.
        val sinToken = tokenEnviado == null && tokenVigente == null
        return when {
            codigo == 401 && (mismaSesion || sinToken) -> 401
            codigo == 403 && mismaSesion -> 403
            else -> null
        }
    }
}
