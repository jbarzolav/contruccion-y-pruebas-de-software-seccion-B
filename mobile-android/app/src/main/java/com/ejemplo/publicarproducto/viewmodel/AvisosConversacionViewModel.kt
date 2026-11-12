package com.ejemplo.publicarproducto.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.conversaciones.ConversacionResumenResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * HU 39 - sondea conversaciones y publica avisos por mensajes entrantes nuevos.
 *
 * R1/R3 - Único responsable del sondeo CUANDO LA PESTAÑA DE CONSULTAS NO ESTÁ
 * ACTIVA Y LA APLICACIÓN ESTÁ EN PRIMER PLANO.
 *
 * El sondeo de `GET /api/conversaciones` solo puede tener un responsable:
 * mientras la pantalla de Consultas está visible, sondea su propio ViewModel
 * (que además refresca la conversación abierta); en el resto de pestañas,
 * este ViewModel sondea para poder avisar en la barra superior.
 *
 * Para que no haya dos sondeos a la vez, la navegación llama a [pausar]
 * cuando entra en Consultas y a [iniciar] cuando sale. Al pausar NO se
 * borra la memoria de los mensajes ya vistos: así, al reanudar, los mensajes
 * que llegaron mientras la pantalla estaba activa se siguen detectando.
 *
 * Con la aplicación en segundo plano, este ViewModel también queda pausado
 * (R1): no hay peticiones HTTP con la app oculta. Al volver al primer plano
 * se reanuda el sondeo.
 */
class AvisosConversacionViewModel(
    private val listarConversaciones: suspend () -> ResultadoApi<List<ConversacionResumenResponse>> = {
        ProveedorDatos.actual.conversaciones.listarConversaciones()
    }
) : ViewModel() {

    var avisoMensaje by mutableStateOf<String?>(null)
        private set

    private var sondeoJob: Job? = null
    private var usuarioEnSondeo: Long? = null
    private val idsMensajesVistos = mutableSetOf<Long>()
    private var cargaInicialCompletada = false

    /**
     * `true` mientras este ViewModel NO sea el responsable del sondeo.
     *
     * Ocurre cuando la pantalla de Consultas sondea o cuando la aplicación
     * está en segundo plano (R1).
     */
    private var cedido = false

    /**
     * Inicia un único sondeo para este ViewModel; las llamadas posteriores no lo duplican.
     */
    fun iniciar(usuarioId: Long) {
        cedido = false
        if (sondeoJob?.isActive == true && usuarioEnSondeo == usuarioId) return
        // Solo se reinicia la memoria de mensajes vistos si cambia el usuario:
        // al volver de la pantalla de Consultas se conserva, para no perder
        // los avisos que llegaron mientras la otra pantalla sondeaba.
        val cambiaUsuario = usuarioEnSondeo != null && usuarioEnSondeo != usuarioId
        sondeoJob?.cancel()
        usuarioEnSondeo = usuarioId
        if (cambiaUsuario) {
            idsMensajesVistos.clear()
            cargaInicialCompletada = false
            avisoMensaje = null
        }

        sondeoJob = viewModelScope.launch {
            while (true) {
                try {
                    when (val resultado = listarConversaciones()) {
                        is ResultadoApi.Exito -> procesarConversaciones(resultado.valor, usuarioId)
                        is ResultadoApi.Error -> Unit // Un fallo temporal no interrumpe el sondeo ni la navegación.
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Fallos inesperados de una iteración tampoco detienen el sondeo.
                }
                delay(INTERVALO_SONDEO_MS)
            }
        }
    }

    /**
     * R1/R3 - Detiene el sondeo de este ViewModel.
     *
     * Se cancela el trabajo en curso (no hay peticiones HTTP) y se conserva
     * la memoria de los mensajes vistos para no perder avisos.
     *
     * Se llama en dos casos:
     * - la pestaña Consultas está activa: el sondeo lo hace su propio
     *   ViewModel (esta pantalla cede el trabajo);
     * - la aplicación está en segundo plano: **nadie** debe sondear, para no
     *   hacer peticiones HTTP con la app oculta.
     *
     * Al volver al primer plano se llama a [iniciar], que reanuda el trabajo
     * sin borrar la memoria de mensajes vistos.
     */
    fun pausar() {
        cedido = true
        sondeoJob?.cancel()
        sondeoJob = null
    }

    /**
     * `true` mientras este ViewModel no sea el responsable del sondeo.
     *
     * Ocurre cuando la pantalla de Consultas sondea o cuando la aplicación
     * está en segundo plano.
     */
    internal fun cedido(): Boolean = cedido

    fun limpiarAviso() {
        avisoMensaje = null
    }

    /** Detiene el sondeo al salir de la sesión autenticada. */
    fun detener() {
        sondeoJob?.cancel()
        sondeoJob = null
        usuarioEnSondeo = null
        idsMensajesVistos.clear()
        cargaInicialCompletada = false
        avisoMensaje = null
        cedido = false
    }

    private fun procesarConversaciones(conversaciones: List<ConversacionResumenResponse>, usuarioId: Long) {
        if (!cargaInicialCompletada) {
            conversaciones.mapNotNullTo(idsMensajesVistos) { it.ultimoMensaje?.id }
            cargaInicialCompletada = true
            return
        }

        conversaciones.forEach { conversacion ->
            val mensaje = conversacion.ultimoMensaje ?: return@forEach
            if (idsMensajesVistos.add(mensaje.id) && mensaje.emisorId != usuarioId) {
                avisoMensaje = conversacion.productoNombre
            }
        }
    }

    private companion object {
        const val INTERVALO_SONDEO_MS = 15_000L
    }
}
