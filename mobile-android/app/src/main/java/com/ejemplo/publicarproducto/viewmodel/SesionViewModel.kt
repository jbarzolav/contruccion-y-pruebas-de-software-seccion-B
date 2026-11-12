package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.session.BusEventosSesion
import com.ejemplo.publicarproducto.model.session.EventoSesion
import com.ejemplo.publicarproducto.model.session.NotificadorErroresAutorizacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface LogoutUiState {
    data object Initial : LogoutUiState
    data object Loading : LogoutUiState
    data object Success : LogoutUiState
    data class Error(val mensaje: String) : LogoutUiState
}

/** HU 23 - Permisos actuales, validación al entrar y avisos de autorización. */
class SesionViewModel(application: Application) : AndroidViewModel(application) {
    // MVVM: la sesión vive en AuthRepositorio (fuera de la vista y del VM).
    private val auth get() = ProveedorDatos.actual.auth
    var usuario by mutableStateOf(auth.usuarioActual())
        private set
    var validando by mutableStateOf(auth.haySesion())
        private set
    var aviso by mutableStateOf<String?>(null)
        private set
    var logoutState by mutableStateOf<LogoutUiState>(LogoutUiState.Initial)
        private set

    init {
        // AUD-19: centraliza la reacción a la expiración del Bearer. El
        // ViewModel no vuelve a publicar SesionExpirada, evitando un ciclo.
        viewModelScope.launch {
            BusEventosSesion.eventos.collect { evento ->
                if (evento == EventoSesion.SesionExpirada) manejarError(401)
            }
        }
        // El cliente HTTP (model/remote) notifica aquí los errores de
        // autorización: el ViewModel los traduce a estado de presentación.
        viewModelScope.launch {
            NotificadorErroresAutorizacion.errores.collect { codigo ->
                manejarError(codigo)
            }
        }
    }

    /** Token vigente para claves de recomposición y polling de conversaciones. */
    fun token(): String? = auth.token()

    fun recargarYValidar() {
        usuario = auth.usuarioActual()
        aviso = null
        validando = usuario != null
        actualizar()
    }
    fun actualizar() {
        val token = auth.token() ?: run { validando = false; return }
        viewModelScope.launch {
            try {
                val resultado = auth.obtenerSesion()
                if (auth.token() != token) return@launch
                when (resultado) {
                    is ResultadoApi.Exito -> {
                        auth.actualizarSesion(resultado.valor)
                        usuario = resultado.valor
                        BusEventosSesion.publicar(EventoSesion.SesionIniciada(resultado.valor.id))
                        programarSincronizacionPendiente(resultado.valor.id)
                    }

                    is ResultadoApi.Error ->
                        if (resultado.codigo == 401) manejarError(401)
                        else aviso = "No se pudieron verificar los permisos. Inténtalo de nuevo."
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { aviso = "No hay conexión para verificar la sesión." }
            finally { validando = false }
        }
    }

    /**
     * Si el usuario vuelve a entrar y tenía operaciones pendientes (por
     * ejemplo, esperando a que la sesión se renovara), se programa la
     * sincronización de la cola persistente.
     */
    private fun programarSincronizacionPendiente(usuarioId: Long) {
        viewModelScope.launch {
            try {
                ProveedorDatos.actual.programacionSincronizacion.programarSiHayPendientes(usuarioId)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
    }

    /**
     * Política segura al cerrar sesión: se borra toda la información local
     * del usuario (notificaciones, pedidos, métricas, umbrales de stock,
     * cola de pendientes y metadatos) para que nadie más pueda verla en el
     * dispositivo.
     */
    private suspend fun limpiarDatosLocales(usuarioId: Long) {
        if (usuarioId <= 0L) return
        val contenedor = try {
            ProveedorDatos.actual.also {
                it.programacionSincronizacion.cancelar(usuarioId)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { return }

        // Un error limpiando una fuente no debe impedir borrar las demás.
        suspend fun intentar(borrar: suspend () -> Unit) {
            try {
                borrar()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Se continúa con el resto de datos locales del usuario.
            }
        }
        intentar { contenedor.notificaciones.borrarUsuario(usuarioId) }
        intentar { contenedor.pedidos.borrarUsuario(usuarioId) }
        // HU 57 - El panel de métricas es información del usuario.
        intentar { contenedor.metricas.borrarUsuario(usuarioId) }
        // HU 58 - Los umbrales de stock son información del usuario.
        intentar { contenedor.umbrales.borrarUsuario(usuarioId) }
        intentar { contenedor.cola.borrarUsuario(usuarioId) }
        intentar { contenedor.metadatosRepositorio.borrarUsuario(usuarioId) }
    }

    fun manejarError(codigo: Int) {
        if (codigo == 401) {
            // R8 - Idempotencia de la expiración.
            //
            // `BusEventosSesion` conserva el último evento (`replay = 1`)
            // porque la expiración puede ocurrir en segundo plano, desde el
            // sincronizador de WorkManager, sin ningún colector activo. Por
            // eso, si la sesión ya está cerrada, la expiración ya se
            // procesó: se omite para no duplicar el aviso, la invalidación
            // ni la limpieza de los datos locales.
            if (usuario == null && !auth.haySesion()) {
                validando = false
                return
            }
            val usuarioId = auth.invalidarSesion()
            usuario = null
            validando = false
            aviso = "Tu sesión ha vencido o es inválida. Inicia sesión nuevamente."
            BusEventosSesion.publicar(EventoSesion.SesionCerrada)
            viewModelScope.launch { limpiarDatosLocales(usuarioId) }
        } else {
            aviso = "No tienes permiso para realizar esta acción."
            actualizar()
        }
    }
    fun volverAlLogin() {
        val usuarioId = auth.invalidarSesion()
        usuario = null
        validando = false
        aviso = null
        BusEventosSesion.publicar(EventoSesion.SesionCerrada)
        viewModelScope.launch { limpiarDatosLocales(usuarioId) }
    }

    fun reiniciarEstadoLogout() { logoutState = LogoutUiState.Initial }

    /** Revoca en servidor con la sesión existente y siempre limpia datos locales al terminar. */
    fun cerrarSesion() {
        if (logoutState is LogoutUiState.Loading) return
        val token = auth.token()
        if (token.isNullOrBlank()) {
            val usuarioId = auth.invalidarSesion()
            usuario = null
            validando = false
            logoutState = LogoutUiState.Success
            BusEventosSesion.publicar(EventoSesion.SesionCerrada)
            viewModelScope.launch { limpiarDatosLocales(usuarioId) }
            return
        }
        logoutState = LogoutUiState.Loading
        viewModelScope.launch {
            var resultado: LogoutUiState = LogoutUiState.Success
            try {
                val respuesta = auth.cerrarSesion()
                // HU 25 - Un 401 significa que la sesión ya no existía en el
                // servidor: para la interfaz es un cierre realizado.
                if (respuesta is ResultadoApi.Error && respuesta.codigo != 401) {
                    resultado = LogoutUiState.Error(respuesta.mensaje)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                resultado = LogoutUiState.Error("No se pudo confirmar el cierre en el servidor; se cerró la sesión local.")
            } finally {
                // El identificador se toma antes de invalidar la sesión.
                val usuarioId = auth.invalidarSesion()
                usuario = null
                validando = false
                logoutState = resultado
                BusEventosSesion.publicar(EventoSesion.SesionCerrada)
                limpiarDatosLocales(usuarioId)
            }
        }
    }

    fun cerrarAviso() { aviso = null }
}
