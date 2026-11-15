package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.ActualizarPerfilRequest
import com.ejemplo.publicarproducto.model.AuthMessageResponse
import com.ejemplo.publicarproducto.model.CodigoRecuperacionRequest
import com.ejemplo.publicarproducto.model.LoginRequest
import com.ejemplo.publicarproducto.model.LoginResponse
import com.ejemplo.publicarproducto.model.PerfilUsuarioResponse
import com.ejemplo.publicarproducto.model.PerfilVendedorRequest
import com.ejemplo.publicarproducto.model.PerfilVendedorResponse
import com.ejemplo.publicarproducto.model.RecuperacionRequest
import com.ejemplo.publicarproducto.model.RecuperacionResponse
import com.ejemplo.publicarproducto.model.RegistroRequest
import com.ejemplo.publicarproducto.model.RestablecerRequest
import com.ejemplo.publicarproducto.model.UsuarioRolResponse
import com.ejemplo.publicarproducto.model.ValidacionCodigoResponse
import com.ejemplo.publicarproducto.model.remote.AuthApi
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.remote.ejecutarPeticion
import com.ejemplo.publicarproducto.model.session.SessionManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Repositorio de autenticación, sesión y perfil (HU 21, 22, 23, 26, 27, 28,
 * 29 y 30).
 *
 * Es el ÚNICO punto de la app que habla con `AuthApi` (Spring Boot `:9000`) y
 * el único que escribe o borra la sesión en `SessionManager`: las pantallas y
 * los ViewModels ya no tocan SharedPreferences ni Retrofit.
 *
 * Reglas conservadas de la implementación anterior:
 * - `iniciarSesion` persiste la sesión solo cuando el login responde 200.
 * - `obtenerSesion` NO persiste nada: quien decide actualizar la identidad
 *   local es el ViewModel, para no romper la guarda "el token cambió" ni la
 *   limpieza de Room de una sesión vencida.
 * - `invalidarSesion` devuelve el identificador del usuario ANTES de borrar
 *   la sesión, porque hace falta para borrar sus datos locales.
 * - Los textos de error son exactamente los que mostraba la interfaz antes
 *   de la refactorización (401 de login -> "Correo o contraseña incorrectos.").
 */
class AuthRepositorio(
    private val api: AuthApi,
    private val sesion: SessionManager,
) {

    // ------------------------------------------------------------ HU 21
    /** Inicia sesión y, solo en éxito, persiste token/identidad/permisos. */
    suspend fun iniciarSesion(correo: String, password: String): ResultadoApi<LoginResponse> {
        val resultado = ejecutarPeticion(
            sinRed = "No hay conexión con el servidor.",
            fallo = "Ocurrió un error inesperado. Inténtalo de nuevo.",
            porCodigo = { codigo ->
                when (codigo) {
                    // Mismo mensaje genérico: no revela si el correo existe.
                    401 -> "Correo o contraseña incorrectos."
                    400 -> "Revisa los datos ingresados."
                    else -> "Ocurrió un error (HTTP $codigo). Inténtalo de nuevo."
                }
            },
            leerMensajes = { it == 400 }
        ) {
            api.login(LoginRequest(correo = correo, password = password))
        }
        if (resultado is ResultadoApi.Exito) guardarSesion(resultado.valor)
        return resultado
    }

    // ------------------------------------------------------------ HU 26
    /** Crea la cuenta. No inicia sesión por sí mismo: decide el ViewModel. */
    suspend fun registrar(request: RegistroRequest): ResultadoApi<AuthMessageResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión con el servidor.",
            fallo = "Ocurrió un error inesperado. Inténtalo de nuevo.",
            porCodigo = { codigo ->
                when (codigo) {
                    // 409 correo duplicado y 400 validaciones traen messages.
                    400, 409 -> "Revisa los datos ingresados."
                    else -> "Ocurrió un error (HTTP $codigo). Inténtalo de nuevo."
                }
            },
            leerMensajes = { it == 400 || it == 409 }
        ) { api.registro(request) }

    // ------------------------------------------------------------ HU 23
    /**
     * Identidad y permisos vigentes en el backend. NO persiste la respuesta:
     * la actualización local la decide el ViewModel tras la guarda de token.
     */
    suspend fun obtenerSesion(): ResultadoApi<UsuarioRolResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión para verificar la sesión.",
            fallo = "No hay conexión para verificar la sesión.",
            porCodigo = { "No se pudieron verificar los permisos. Inténtalo de nuevo." }
        ) { api.obtenerSesion() }

    /** Revoca la sesión en el servidor (el 401 se trata como cierre hecho). */
    suspend fun cerrarSesion(): ResultadoApi<AuthMessageResponse> =
        ejecutarPeticion(
            sinRed = "No se pudo confirmar el cierre en el servidor; se cerró la sesión local.",
            fallo = "No se pudo confirmar el cierre en el servidor; se cerró la sesión local.",
            porCodigo = { "No se pudo confirmar el cierre; se cerró la sesión local." },
            leerMensajes = { true }
        ) { api.cerrarSesion() }

    // ------------------------------------------------- acceso a la sesión
    fun token(): String? = sesion.obtenerToken()

    fun haySesion(): Boolean = sesion.haySesion()

    fun usuarioActual(): UsuarioRolResponse? = sesion.obtenerUsuario()

    fun usuarioId(): Long = sesion.obtenerUsuarioId()

    /** Refleja en local la identidad confirmada por el backend. */
    fun actualizarSesion(usuario: UsuarioRolResponse) = sesion.actualizarUsuario(usuario)

    /**
     * Borra la sesión local y devuelve el identificador del usuario para que
     * el llamador pueda borrar sus datos de Room.
     */
    fun invalidarSesion(): Long {
        val usuarioId = sesion.obtenerUsuarioId()
        sesion.invalidarSesion()
        return usuarioId
    }

    // ------------------------------------------------------------ HU 22
    suspend fun solicitarRecuperacion(correo: String): ResultadoApi<RecuperacionResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión con el servidor.",
            fallo = "No se pudo completar la solicitud. Inténtalo de nuevo.",
            porCodigo = { "No se pudo completar la solicitud. Inténtalo de nuevo." },
            leerMensajes = { true }
        ) {
            api.solicitarRecuperacion(RecuperacionRequest(correo))
        }

    /**
     * Verifica el código de 6 dígitos antes de permitir la nueva contraseña
     * (mismo flujo que el web). No consume el código: solo lo valida.
     * Errores exactos del backend: 400 "El código no es válido o ya venció."
     * y 429 "Demasiados intentos. Espera un minuto y vuelve a intentarlo."
     */
    suspend fun verificarCodigo(codigo: String): ResultadoApi<ValidacionCodigoResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión con el servidor.",
            fallo = "No se pudo verificar el código. Inténtalo de nuevo.",
            porCodigo = { codigoHttp ->
                when (codigoHttp) {
                    429 -> "Demasiados intentos. Espera un minuto y vuelve a intentarlo."
                    else -> "El código no es válido o ya venció. Solicita uno nuevo."
                }
            },
            leerMensajes = { true }
        ) {
            api.verificarCodigoRecuperacion(CodigoRecuperacionRequest(codigo))
        }

    suspend fun restablecer(
        token: String,
        password: String,
        confirmacion: String,
    ): ResultadoApi<RecuperacionResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión con el servidor.",
            fallo = "No se pudo completar la solicitud. Inténtalo de nuevo.",
            porCodigo = { "No se pudo completar la solicitud. Inténtalo de nuevo." },
            leerMensajes = { true }
        ) {
            api.restablecer(RestablecerRequest(token, password, confirmacion))
        }

    // ------------------------------------------------------- HU 27 / HU 28
    suspend fun obtenerPerfil(): ResultadoApi<PerfilUsuarioResponse> =
        ejecutarPeticion(
            sinRed = "No se pudo conectar con el servidor.",
            fallo = "No se pudo conectar con el servidor.",
            porCodigo = { "No se pudo cargar el perfil." }
        ) { api.obtenerPerfil() }

    suspend fun actualizarPerfil(request: ActualizarPerfilRequest): ResultadoApi<PerfilUsuarioResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión con el servidor.",
            fallo = "Ocurrió un error inesperado. Inténtalo de nuevo.",
            porCodigo = { "No se pudo actualizar el perfil (HTTP $it)." }
        ) { api.actualizarPerfil(request) }

    // ---------------------------------------------------------------- HU 30
    /**
     * Sube la foto del perfil. El repositorio arma el `MultipartBody.Part`
     * para que el ViewModel no dependa de Retrofit.
     */
    suspend fun subirFotoPerfil(bytes: ByteArray, extension: String): ResultadoApi<PerfilUsuarioResponse> {
        val mediaType = if (extension == "png") "image/png" else "image/jpeg"
        val archivo = MultipartBody.Part.createFormData(
            "file",
            "foto-perfil.$extension",
            bytes.toRequestBody(mediaType.toMediaTypeOrNull())
        )
        return ejecutarPeticion(
            sinRed = "No hay conexión con el servidor o no se pudo leer la imagen.",
            fallo = "No se pudo actualizar la foto. Inténtalo de nuevo.",
            porCodigo = { "No se pudo actualizar la foto (HTTP $it)." }
        ) { api.actualizarFotoPerfil(archivo) }
    }

    // ---------------------------------------------------------------- HU 29
    suspend fun obtenerPerfilVendedor(): ResultadoApi<PerfilVendedorResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión con el servidor.",
            fallo = "No se pudo cargar la información del vendedor.",
            porCodigo = { "No se pudo cargar la información del vendedor." }
        ) { api.obtenerPerfilVendedor() }

    suspend fun guardarPerfilVendedor(request: PerfilVendedorRequest): ResultadoApi<PerfilVendedorResponse> =
        ejecutarPeticion(
            sinRed = "No hay conexión con el servidor.",
            fallo = "Ocurrió un error inesperado. Inténtalo de nuevo.",
            porCodigo = { "No se pudo guardar (HTTP $it)." }
        ) { api.guardarPerfilVendedor(request) }

    // ------------------------------------------------------------ HU 21
    /** Persiste la sesión local sin contraseña (la decide el Bearer). */
    private fun guardarSesion(respuesta: LoginResponse) {
        sesion.guardarSesion(
            token = respuesta.token,
            id = respuesta.id,
            correo = respuesta.correo,
            rol = respuesta.rol,
            permisos = respuesta.permisos
        )
    }
}
