package com.ejemplo.publicarproducto.view.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Estado de navegación de la aplicación.
 *
 * Contiene la pestaña activa y las subpantallas abiertas (detalle de
 * producto, edición, publicación, confirmación de compra, detalle de pedido y
 * conversación consultada). Solo guarda estado de presentación: no consulta
 * datos ni toma decisiones de negocio.
 */
@Stable
class EstadoNavegacion {

    /** Pestaña activa de la barra inferior o de los accesos superiores. */
    var pestana by mutableStateOf(IndicesNavegacion.INICIO)
        private set

    /** HU 07 - producto abierto en la pantalla de detalle. */
    var productoDetalleId by mutableStateOf<Long?>(null)
        private set

    /** HU 03 - producto abierto en la pantalla de edición. */
    var productoEnEdicionId by mutableStateOf<Long?>(null)
        private set

    /** HU 01/02 - pantalla de publicación abierta. */
    var publicando by mutableStateOf(false)
        private set

    /** HU 15 - pantalla de confirmación de compra abierta. */
    var confirmandoCompra by mutableStateOf(false)
        private set

    /** HU 50 - pedido propio abierto en su pantalla de detalle. */
    var pedidoDetalleId by mutableStateOf<Long?>(null)
        private set

    /** HU 39/HU 53 - conversación a la que se debe saltar. */
    var conversacionPendienteId by mutableStateOf<Long?>(null)
        private set

    /** Las subpantallas ocultan la barra superior y la barra inferior. */
    val enPantallaPrincipal: Boolean
        get() = productoDetalleId == null &&
            productoEnEdicionId == null &&
            !publicando &&
            !confirmandoCompra &&
            pedidoDetalleId == null

    fun irA(indice: Int) {
        pestana = indice
    }

    fun abrirProducto(id: Long) {
        productoDetalleId = id
    }

    fun cerrarProducto() {
        productoDetalleId = null
    }

    fun abrirEdicion(id: Long) {
        productoEnEdicionId = id
    }

    fun cerrarEdicion() {
        productoEnEdicionId = null
    }

    fun abrirPublicacion() {
        publicando = true
    }

    fun cerrarPublicacion() {
        publicando = false
    }

    fun abrirConfirmacionCompra() {
        confirmandoCompra = true
    }

    fun cerrarConfirmacionCompra() {
        confirmandoCompra = false
    }

    fun abrirPedido(id: Long) {
        pedidoDetalleId = id
    }

    fun cerrarPedido() {
        pedidoDetalleId = null
    }

    fun abrirConversacion(id: Long) {
        conversacionPendienteId = id
    }

    fun limpiarConversacion() {
        conversacionPendienteId = null
    }
}

/**
 * Crea el estado de navegación recordándolo por [clave].
 *
 * La clave (identidad y rol del usuario) reinicia el estado cuando cambia la
 * sesión, igual que antes de extraer la navegación.
 */
@Composable
fun rememberEstadoNavegacion(clave: Any?): EstadoNavegacion =
    remember(clave) { EstadoNavegacion() }
