package com.ejemplo.publicarproducto.model.session

import android.content.Context
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HU 21 - persistencia de la sesión.
 *
 * HU 23 - Guarda token, identidad, rol y permisos del usuario.
 * AUD-14 - Delegación en un store sustituible y token cifrado en producción.
 * La contraseña NUNCA se almacena aquí (ni el hash): solo viaja en la
 * petición de login. No hay logout ni refresh token en esta historia
 * (corresponden a historias posteriores).
 *
 * R2 - Identidad reactiva.
 *
 * `usuarioIdFlow` es la ÚNICA fuente de la identidad activa: publica en un
 * `StateFlow` el identificador del usuario de la sesión vigente. Los
 * ViewModels que observan datos protegidos se suscriben a este flujo, de modo
 * que al cerrar sesión y entrar con otra cuenta en la misma `Activity` se
 * cancelan los collectors del usuario anterior y se observan los del nuevo.
 * Antes, la identidad se capturaba en el `init` del ViewModel y quedaba
 * congelada durante toda la vida de la pantalla.
 */
class SessionManager(
    private val store: SesionStore
) {

    /**
     * Constructor de producción: guarda la sesión cifrada en
     * `SharedPreferences` (nunca en claro).
     */
    constructor(context: Context) : this(SesionStorePreferencias(context))

    /**
     * Constructor con store explícito.
     *
     * Se conserva por compatibilidad con las pruebas que inyectan un store
     * en memoria; el `context` no interviene porque el store ya lo lleva.
     */
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context, store: SesionStore) : this(store)

    /** Identidad activa, publicada de forma reactiva (R2). */
    private val _usuarioIdFlow = MutableStateFlow(store.leer().usuarioId)

    /** Identidad activa del usuario de la sesión vigente (R2). */
    val usuarioIdFlow: StateFlow<Long> = _usuarioIdFlow.asStateFlow()

    fun guardarSesion(token: String, id: Long, correo: String, rol: String = "", permisos: Set<String> = emptySet()) {
        store.guardar(
            SesionGuardada(
                token = token,
                usuarioId = id,
                correo = correo,
                rol = rol,
                permisos = permisos
            )
        )
        publicarUsuarioId(id)
    }

    /** Control de UI local: ¿hay token guardado? (sin validación remota) */
    fun haySesion(): Boolean = !obtenerToken().isNullOrBlank()

    fun obtenerToken(): String? = store.leer().token

    // HU 23 - Identidad y permisos actuales de la sesión.
    fun obtenerUsuarioId(): Long = store.leer().usuarioId
    fun obtenerUsuario(): UsuarioRolResponse? {
        if (!haySesion()) return null
        val sesion = store.leer()
        return UsuarioRolResponse(sesion.usuarioId, sesion.correo, sesion.rol, sesion.permisos)
    }
    fun actualizarUsuario(usuario: UsuarioRolResponse) {
        store.leer().token?.let { token ->
            store.guardar(
                SesionGuardada(
                    token = token,
                    usuarioId = usuario.id,
                    correo = usuario.correo,
                    rol = usuario.rol,
                    permisos = usuario.permisos
                )
            )
            publicarUsuarioId(usuario.id)
        }
    }
    fun invalidarSesion() {
        store.limpiar()
        publicarUsuarioId(0L)
    }

    /**
     * Publica la identidad activa en [usuarioIdFlow].
     *
     * Solo se emite cuando el identificador cambia: así, un `logout` seguido
     * de un `login` del mismo usuario no reinicia los collectors de forma
     * innecesaria.
     */
    private fun publicarUsuarioId(usuarioId: Long) {
        if (_usuarioIdFlow.value != usuarioId) {
            _usuarioIdFlow.value = usuarioId
        }
    }
}
