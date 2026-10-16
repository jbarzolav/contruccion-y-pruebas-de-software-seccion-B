package com.ejemplo.publicarproducto.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.network.RetrofitClient
import com.ejemplo.publicarproducto.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** HU 23 - Permisos actuales, validación al entrar y avisos de autorización. */
class SesionViewModel(application: Application) : AndroidViewModel(application) {
    val sesion = SessionManager(application)
    var usuario by mutableStateOf(sesion.obtenerUsuario())
        private set
    var validando by mutableStateOf(sesion.haySesion())
        private set
    var aviso by mutableStateOf<String?>(null)
        private set

    fun recargarYValidar() {
        usuario = sesion.obtenerUsuario()
        aviso = null
        validando = usuario != null
        actualizar()
    }
    fun actualizar() {
        val token = sesion.obtenerToken() ?: run { validando = false; return }
        viewModelScope.launch {
            try {
                val respuesta = RetrofitClient.authApi.obtenerSesion()
                if (sesion.obtenerToken() != token) return@launch
                val actual = respuesta.body()
                if (respuesta.isSuccessful && actual != null) {
                    sesion.actualizarUsuario(actual)
                    usuario = actual
                } else if (respuesta.code() == 401) manejarError(401)
                else aviso = "No se pudieron verificar los permisos. Inténtalo de nuevo."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { aviso = "No hay conexión para verificar la sesión." }
            finally { validando = false }
        }
    }
    fun manejarError(codigo: Int) {
        if (codigo == 401) {
            sesion.invalidarSesion()
            usuario = null
            validando = false
            aviso = "Tu sesión ha vencido o es inválida. Inicia sesión nuevamente."
        } else {
            aviso = "No tienes permiso para realizar esta acción."
            actualizar()
        }
    }
    fun volverAlLogin() {
        sesion.invalidarSesion()
        usuario = null
        validando = false
        aviso = null
    }
    fun cerrarAviso() { aviso = null }
}
