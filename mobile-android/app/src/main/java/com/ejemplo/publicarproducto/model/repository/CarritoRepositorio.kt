package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.CarritoTotalResponse
import com.ejemplo.publicarproducto.model.ItemCarritoCantidadRequest
import com.ejemplo.publicarproducto.model.ItemCarritoRequest
import com.ejemplo.publicarproducto.model.ItemCarritoResponse
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.remote.ejecutarPeticion
import com.ejemplo.publicarproducto.model.remote.mensajesDeError
import kotlinx.coroutines.CancellationException

/**
 * Repositorio del carrito de compras (HU 11, HU 12, HU 13 y HU 14).
 *
 * Es el ÚNICO punto de la app que habla con los endpoints de carrito: las
 * pantallas y los ViewModels ya no conocen `ProductoApi`.
 *
 * Reglas conservadas de la implementación anterior:
 * - El subtotal de cada ítem y el total del carrito los calcula SIEMPRE el
 *   backend: aquí no se calcula nada en local.
 * - Los textos de error son exactamente los que mostraba la interfaz antes
 *   de la refactorización, incluido el detalle de la excepción.
 * - El carrito es una operación EN LÍNEA: no hay entidad de carrito en Room,
 *   no se encola ninguna operación y no interviene WorkManager. El
 *   comportamiento Offline First del resto de la app no se toca.
 *
 * El checkout (registro del pedido, calificación y reseña) NO vive aquí:
 * pertenece a [PedidosRepositorio].
 */
class CarritoRepositorio(
    private val api: ProductoApi,
) {

    companion object {
        /** Texto de red que ya mostraban las pantallas de carrito. */
        private const val SIN_CONEXION = "No hay conexión con el backend (10.0.2.2:9000): "
    }

    /** El carrito muestra solo el primer mensaje de `messages[]`. */
    private val primerMensaje: (List<String>) -> String = { lista -> lista.firstOrNull().orEmpty() }

    // ------------------------------------------------------------------ HU 11
    /** Agrega un producto al carrito con la cantidad elegida. */
    suspend fun agregarItem(
        idProducto: Long,
        cantidad: Int,
    ): ResultadoApi<ItemCarritoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { "Error $it: no se pudo agregar." },
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) { api.agregarAlCarrito(ItemCarritoRequest(productoId = idProducto, cantidad = cantidad)) }

    // ------------------------------------------------------------------ HU 12
    /** Ítems del carrito del cliente autenticado. */
    suspend fun obtenerCarrito(): ResultadoApi<List<ItemCarritoResponse>> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { "Error $it: no se pudo cargar el carrito." },
            vacio = { emptyList() }
        ) { api.obtenerCarrito() }

    /**
     * Cambia la cantidad de un ítem (HU 12).
     *
     * El backend valida el stock y devuelve el ítem con el subtotal ya
     * recalculado.
     */
    suspend fun actualizarCantidad(
        idItem: Long,
        cantidad: Int,
    ): ResultadoApi<ItemCarritoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { "Error $it: no se pudo actualizar." },
            leerMensajes = { true },
            formatearMensajes = primerMensaje
        ) {
            api.actualizarCantidadItem(
                idItemCarrito = idItem,
                request = ItemCarritoCantidadRequest(cantidad = cantidad)
            )
        }

    // ------------------------------------------------------------------ HU 13
    /**
     * Elimina un ítem del carrito (HU 13).
     *
     * El endpoint responde 204 sin cuerpo, por eso el resultado se expresa
     * con `Unit`.
     */
    suspend fun eliminarItem(idItem: Long): ResultadoApi<Unit> {
        return try {
            val respuesta = api.eliminarItemCarrito(idItem)
            if (respuesta.isSuccessful) {
                ResultadoApi.Exito(Unit)
            } else {
                val codigo = respuesta.code()
                val mensaje = mensajesDeError(respuesta)?.firstOrNull()
                    ?: "Error $codigo: no se pudo actualizar."
                ResultadoApi.Error(
                    codigo = codigo,
                    mensaje = mensaje,
                    temporal = codigo >= 500 || codigo == 408 || codigo == 429
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoApi.Error(
                codigo = null,
                mensaje = "$SIN_CONEXION${e.message}",
                temporal = true
            )
        }
    }

    // ------------------------------------------------------------------ HU 14
    /** Total del carrito calculado por Spring Boot. */
    suspend fun obtenerTotal(): ResultadoApi<CarritoTotalResponse> =
        ejecutarPeticion(
            excepcion = { e -> "No se pudo consultar el total: ${e.message}" },
            porCodigo = { "Error $it: no se pudo consultar el total." }
        ) { api.obtenerTotalCarrito() }
}
