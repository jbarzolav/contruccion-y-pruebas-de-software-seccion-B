package com.ejemplo.publicarproducto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Mejora técnica Offline First - avisos de estado de la copia local.
 *
 * No rediseña la pantalla: solo informa de
 * - el modo sin conexión (se muestran los últimos datos sincronizados),
 * - las operaciones guardadas en el dispositivo que todavía NO confirmó el
 *   servidor,
 * - el aviso de sincronización completada,
 * - el aviso de rechazo del servidor.
 */
@Composable
fun IndicadorOffline(
    sinConexion: Boolean,
    pendientes: Long,
    aviso: String? = null,
    rechazo: String? = null,
    modifier: Modifier = Modifier
) {
    if (!sinConexion && pendientes <= 0L && aviso.isNullOrBlank() && rechazo.isNullOrBlank()) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (sinConexion) {
                Text(
                    "Sin conexión: se muestran los últimos datos sincronizados.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (pendientes > 0L) {
                Text(
                    "$pendientes operación(es) pendiente(s) de sincronizar con el servidor.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            aviso?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            rechazo?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
