package com.ejemplo.publicarproducto.session

import android.content.Context

/**
 * HU 21 - persistencia de la sesión en SharedPreferences.
 *
 * Guarda únicamente token (UUID opaco), id y correo del usuario.
 * La contraseña NUNCA se almacena aquí (ni el hash): solo viaja en la
 * petición de login. No hay logout ni refresh token en esta historia
 * (corresponden a historias posteriores).
 */
class SessionManager(context: Context) {

    private val prefs =
        context.getSharedPreferences("pulgatec_sesion", Context.MODE_PRIVATE)

    fun guardarSesion(token: String, id: Long, correo: String) {
        prefs.edit()
            .putString("pulgatec_token", token)
            .putLong("pulgatec_usuario_id", id)
            .putString("pulgatec_usuario_correo", correo)
            .apply()
    }

    /** Control de UI local: ¿hay token guardado? (sin validación remota) */
    fun haySesion(): Boolean = !obtenerToken().isNullOrBlank()

    fun obtenerToken(): String? = prefs.getString("pulgatec_token", null)
}
