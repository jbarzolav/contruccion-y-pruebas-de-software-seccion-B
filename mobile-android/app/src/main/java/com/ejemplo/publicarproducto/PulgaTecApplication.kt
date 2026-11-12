package com.ejemplo.publicarproducto

import android.app.Application
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.remote.UserApiClient
import com.ejemplo.publicarproducto.model.sync.ObservadorConectividad

/**
 * Aplicación de PulgaTec.
 *
 * - Configura la sesión compartida (`SessionManager`) para que el cliente
 *   Retrofit existente añada el Bearer vigente, también desde el
 *   sincronizador de WorkManager.
 * - Abre la base local de Room (una sola instancia por proceso).
 * - Arranca la observación de conectividad para programar la sincronización
 *   de las operaciones pendientes cuando vuelve la red.
 *
 * R9 - La aplicación es la dueña del observador de conectividad: cuando el
 * proceso termina, se cancela su trabajo de forma explícita.
 */
class PulgaTecApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // El contenedor de Model es el dueño de la sesión compartida: el cliente
        // HTTP usa la MISMA instancia que el repositorio de autenticación, así
        // no hay dos copias del token en el proceso.
        ProveedorDatos.inicializar(this)
        UserApiClient.configurarSesion(ProveedorDatos.actual.sesion)
        ObservadorConectividad.iniciar(this)
    }

    override fun onTerminate() {
        // R9 - Ruta explícita de parada del observador de conectividad.
        ObservadorConectividad.detener()
        super.onTerminate()
    }
}
