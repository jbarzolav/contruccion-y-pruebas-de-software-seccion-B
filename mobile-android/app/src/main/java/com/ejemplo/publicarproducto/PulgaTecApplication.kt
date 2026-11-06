package com.ejemplo.publicarproducto

import android.app.Application
import com.ejemplo.publicarproducto.data.ProveedorDatos
import com.ejemplo.publicarproducto.network.UserApiClient
import com.ejemplo.publicarproducto.session.SessionManager
import com.ejemplo.publicarproducto.sync.ObservadorConectividad

/**
 * Aplicación de PulgaTec.
 *
 * - Configura la sesión compartida (`SessionManager`) para que el cliente
 *   Retrofit existente añada el Bearer vigente, también desde el
 *   sincronizador de WorkManager.
 * - Abre la base local de Room (una sola instancia por proceso).
 * - Arranca la observación de conectividad para programar la sincronización
 *   de las operaciones pendientes cuando vuelve la red.
 */
class PulgaTecApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val sesion = SessionManager(this)
        // El cliente Retrofit existente se reutiliza: no hay una segunda
        // implementación de red. MainActivity sustituye este manejador por el
        // suyo para mostrar los avisos de autorización en la interfaz.
        UserApiClient.configurarSesion(sesion) { }
        ProveedorDatos.inicializar(this)
        ObservadorConectividad.iniciar(this)
    }
}
