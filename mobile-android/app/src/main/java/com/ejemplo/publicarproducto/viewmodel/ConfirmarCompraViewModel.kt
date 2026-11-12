package com.ejemplo.publicarproducto.viewmodel

import com.ejemplo.publicarproducto.domain.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ejemplo.publicarproducto.model.repository.CarritoRepositorio
import com.ejemplo.publicarproducto.model.repository.PedidosRepositorio
import com.ejemplo.publicarproducto.model.di.ProveedorDatos
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
import com.ejemplo.publicarproducto.model.PedidoResponse
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import kotlinx.coroutines.launch
import java.math.BigDecimal

sealed interface ConfirmarCompraUiState {
    data object Loading : ConfirmarCompraUiState
    data object Success : ConfirmarCompraUiState
    data class Error(val mensaje: String) : ConfirmarCompraUiState
}

/**
 * HU 15 - "Confirmar mi compra": resumen del carrito (lista, subtotales
 * y total) y registro del pedido con POST /api/pedidos.
 * HU 41 / HU 42 - Calificación y reseña del pedido recién comprado.
 *
 * Reglas del backend: carrito vacío → 400, stock insuficiente → 400
 * (no se descuenta nada) y, al tener éxito, se descuenta el stock
 * y se vacía el carrito.
 *
 * El ViewModel no habla con Retrofit: el carrito y el total vienen de
 * [CarritoRepositorio] y el checkout de [PedidosRepositorio].
 */
class ConfirmarCompraViewModel : ViewModel() {

    /** Repositorio del carrito (se resuelve en cada llamada). */
    private val repositorioCarrito: CarritoRepositorio
        get() = ProveedorDatos.actual.carrito

    /** Repositorio de pedidos (se resuelve en cada llamada). */
    private val repositorioPedidos: PedidosRepositorio
        get() = ProveedorDatos.actual.pedidos

    var items by mutableStateOf<List<ItemCarritoResponse>>(emptyList())
        private set

    var totalCarrito by mutableStateOf(BigDecimal.ZERO)
        private set

    var uiState by mutableStateOf<ConfirmarCompraUiState>(ConfirmarCompraUiState.Loading)
        private set

    /** Pedido registrado (comprobante con el ID). */
    var pedido by mutableStateOf<PedidoResponse?>(null)
        private set

    var confirmando by mutableStateOf(false)
        private set

    var mensajeError by mutableStateOf<String?>(null)
        private set

    var valorCalificacion by mutableStateOf(0)
    var destinoResena by mutableStateOf("PRODUCTO")
    var textoResena by mutableStateOf("")
    var guardandoExperiencia by mutableStateOf(false)
        private set
    var mensajeExperiencia by mutableStateOf<String?>(null)
        private set

    /** Carga el resumen final: ítems + total calculado por el servidor. */
    fun cargarResumen() {
        uiState = ConfirmarCompraUiState.Loading
        mensajeError = null

        viewModelScope.launch {
            when (val resultado = repositorioCarrito.obtenerCarrito()) {
                is ResultadoApi.Exito -> {
                    items = resultado.valor
                    val resultadoTotal = repositorioCarrito.obtenerTotal()
                    when (resultadoTotal) {
                        is ResultadoApi.Exito -> {
                            totalCarrito = resultadoTotal.valor.total
                            uiState = ConfirmarCompraUiState.Success
                        }
                        is ResultadoApi.Error -> {
                            uiState = ConfirmarCompraUiState.Error(
                                if (resultadoTotal.codigo == null) resultadoTotal.mensaje
                                else "Error ${resultadoTotal.codigo}: no se pudo cargar el resumen."
                            )
                        }
                    }
                }

                is ResultadoApi.Error -> {
                    // Se conserva el texto que ya mostraba esta pantalla:
                    // "Error N: no se pudo cargar el resumen." y el detalle
                    // de red cuando no hubo respuesta HTTP.
                    uiState = ConfirmarCompraUiState.Error(
                        if (resultado.codigo == null) {
                            resultado.mensaje
                        } else {
                            "Error ${resultado.codigo}: no se pudo cargar el resumen."
                        }
                    )
                }
            }
        }
    }

    /** HU 15 - POST /api/pedidos: registra el pedido y vacía el carrito. */
    fun confirmarCompra() {
        if (confirmando || items.isEmpty()) return

        confirmando = true
        mensajeError = null

        viewModelScope.launch {
            when (val resultado = repositorioPedidos.confirmarCompra()) {
                is ResultadoApi.Exito -> {
                    pedido = resultado.valor
                    items = emptyList()
                    totalCarrito = BigDecimal.ZERO
                }

                is ResultadoApi.Error -> mensajeError = resultado.mensaje
            }
            confirmando = false
        }
    }

    /** Vuelve al modo resumen (por ejemplo, tras ver el comprobante). */
    fun reiniciar() {
        pedido = null
        cargarResumen()
    }

    fun calificar(valor: Int) {
        val idPedido = pedido?.idPedido ?: return
        if (guardandoExperiencia || pedido?.calificacion != null) return
        valorCalificacion = valor
        guardandoExperiencia = true
        mensajeExperiencia = null
        viewModelScope.launch {
            when (val resultado = repositorioPedidos.calificarPedido(idPedido, valor)) {
                is ResultadoApi.Exito -> pedido = resultado.valor

                is ResultadoApi.Error -> mensajeExperiencia = resultado.mensaje
            }
            guardandoExperiencia = false
        }
    }

    fun resenar() {
        val id = pedido?.idPedido ?: return
        val texto = textoResena.trim()
        if (guardandoExperiencia || texto.isEmpty() || texto.length > 1000) return
        guardandoExperiencia = true
        mensajeExperiencia = null
        viewModelScope.launch {
            when (val resultado = repositorioPedidos.resenarPedido(id, destinoResena, texto)) {
                is ResultadoApi.Exito -> {
                    pedido = resultado.valor
                    textoResena = ""
                }

                is ResultadoApi.Error -> mensajeExperiencia = resultado.mensaje
            }
            guardandoExperiencia = false
        }
    }
}
