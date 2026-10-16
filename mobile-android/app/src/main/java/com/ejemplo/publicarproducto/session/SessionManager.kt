package com.ejemplo.publicarproducto.session

import android.content.Context
import com.ejemplo.publicarproducto.model.UsuarioRolResponse

/**
 * HU 21 - persistencia de la sesión en SharedPreferences.
 *
 * HU 23 - Guarda token, identidad, rol y permisos del usuario.
 * La contraseña NUNCA se almacena aquí (ni el hash): solo viaja en la
 * petición de login. No hay logout ni refresh token en esta historia
 * (corresponden a historias posteriores).
 */
class SessionManager(context: Context) {

    private val prefs =
        context.getSharedPreferences("pulgatec_sesion", Context.MODE_PRIVATE)

    fun guardarSesion(token: String, id: Long, correo: String, rol: String = "", permisos: Set<String> = emptySet()) {
        prefs.edit()
            .putString("pulgatec_token", token)
            .putLong("pulgatec_usuario_id", id)
            .putString("pulgatec_usuario_correo", correo)
            .putString("pulgatec_rol", rol)
            .putStringSet("pulgatec_permisos", permisos)
            .apply()
    }

    /** Control de UI local: ¿hay token guardado? (sin validación remota) */
    fun haySesion(): Boolean = !obtenerToken().isNullOrBlank()

    fun obtenerToken(): String? = prefs.getString("pulgatec_token", null)

    // HU 23 - Identidad y permisos actuales de la sesión.
    fun obtenerUsuarioId(): Long = prefs.getLong("pulgatec_usuario_id", 0L)
    fun obtenerUsuario(): UsuarioRolResponse? {
        if (!haySesion()) return null
        return UsuarioRolResponse(obtenerUsuarioId(), prefs.getString("pulgatec_usuario_correo", "") ?: "",
            prefs.getString("pulgatec_rol", "") ?: "", prefs.getStringSet("pulgatec_permisos", emptySet())?.toSet() ?: emptySet())
    }
    fun actualizarUsuario(usuario: UsuarioRolResponse) {
        obtenerToken()?.let { guardarSesion(it, usuario.id, usuario.correo, usuario.rol, usuario.permisos) }
    }
    fun invalidarSesion() { prefs.edit().clear().apply() }
}
