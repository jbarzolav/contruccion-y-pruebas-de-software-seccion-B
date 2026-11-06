package com.ejemplo.publicarproducto.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Detección de conectividad del dispositivo.
 *
 * Solo informa del estado de la red: no decide nada sobre el backend. La
 * fuente oficial de los datos sigue siendo el servidor.
 */
class MonitorConectividad(context: Context) {

    private val conectividad =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** ¿Hay una red disponible y validada en este momento? */
    fun hayConexion(): Boolean {
        val red = conectividad.activeNetwork ?: return false
        val capacidades = conectividad.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Emite el estado de la conexión cada vez que cambia. Se usa para
     * programar la sincronización en cuanto vuelve la red.
     */
    fun observar(): Flow<Boolean> = callbackFlow {
        trySend(hayConexion())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(hayConexion())
            }

            override fun onLost(network: Network) {
                trySend(hayConexion())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(hayConexion())
            }
        }
        try {
            conectividad.registerDefaultNetworkCallback(callback)
        } catch (_: SecurityException) {
            // Sin permiso para observar la red: se mantiene el valor inicial.
        }
        awaitClose { runCatching { conectividad.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
