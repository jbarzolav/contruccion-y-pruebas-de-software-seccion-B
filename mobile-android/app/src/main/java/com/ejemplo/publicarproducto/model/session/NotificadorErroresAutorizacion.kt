package com.ejemplo.publicarproducto.model.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Notificador de errores de autorización detectados por el cliente HTTP.
 *
 * El interceptor de `model/remote` publica aquí los códigos 401/403 y la capa
 * de presentación los consume desde su ViewModel (`SesionViewModel`). De este
 * modo la `Activity` no participa en el flujo de errores HTTP: solo dibuja el
 * estado que publica el ViewModel.
 *
 * - 401: la sesión caducó. Se publica en el bus de sesión, que ya conserva
 *   memoria (`replay`) para que un ViewModel nuevo también conozca el estado.
 * - 403: el usuario no tiene permiso. Se entrega sin `replay` porque es un
 *   aviso puntual de la petición concreta: así no se muestran avisos
 *   obsoletos al recrear la pantalla.
 */
object NotificadorErroresAutorizacion {

    private const val CAPACIDAD_EXTRA = 16

    private val fuente = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = CAPACIDAD_EXTRA
    )

    /** Códigos de autorización denegada (403) pendientes de consumir. */
    val errores: SharedFlow<Int> = fuente.asSharedFlow()

    /**
     * Traduce la respuesta HTTP al evento que corresponde.
     *
     * @param codigo código de respuesta (401 = sesión caducada, 403 = sin permiso).
     */
    fun notificar(codigo: Int) {
        when (codigo) {
            401 -> BusEventosSesion.publicar(EventoSesion.SesionExpirada)
            403 -> fuente.tryEmit(codigo)
        }
    }
}
