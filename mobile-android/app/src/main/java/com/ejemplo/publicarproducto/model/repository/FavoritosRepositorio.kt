package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.FavoritoResponse
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.remote.ejecutarPeticion
import kotlinx.coroutines.CancellationException

/**
 * Repositorio de favoritos del cliente autenticado (HU 31, HU 32, HU 33 y HU 35).
 *
 * Vive APARTE de [ProductosRepositorio]: los favoritos son una relación del
 * usuario con un producto, no una operación del catálogo. Los ViewModels
 * delegan aquí las cuatro operaciones y no conocen `ProductoApi`.
 *
 * Conserva los textos que mostraba la interfaz antes de la refactorización:
 * el error por código ("Error N: ...") y el mensaje de red de cada acción.
 */
class FavoritosRepositorio(
    private val api: ProductoApi,
) {

    // ------------------------------------------------------- HU 31 / HU 32
    /**
     * Favoritos del cliente autenticado.
     *
     * HU 32 - Lista de favoritos: la pantalla de favoritos pinta los datos
     * públicos del producto guardado.
     *
     * Ruta real: `GET /api/usuarios/me/favoritos` (no existe un
     * `GET /api/favoritos`). El backend responde con
     * `FavoritoProductoResponse`, que tiene 9 campos y NO incluye
     * `vendedorId`: por eso el tipo de retorno declara `vendedorId` como
     * nulo. Los campos que usa la tarjeta de producto (nombre, precio,
     * imagen, stock, categoría, estado) sí llegan completos.
     */
    suspend fun listarFavoritos(): ResultadoApi<List<ProductoResponse>> =
        ejecutarPeticion(
            excepcion = { "No hay conexión para cargar tus favoritos." },
            porCodigo = { "Error $it: no se pudieron cargar tus favoritos." },
            vacio = { emptyList() }
        ) { api.listarFavoritos() }

    // ------------------------------------------------------------------ HU 35
    /** Estado puntual del favorito de un producto. */
    suspend fun consultarEstadoFavorito(
        idProducto: Long,
    ): ResultadoApi<FavoritoResponse> =
        ejecutarPeticion(
            excepcion = { "No hay conexión para consultar tus favoritos." },
            porCodigo = { "Error $it: no se pudo consultar el estado del favorito." }
        ) { api.consultarEstadoFavorito(idProducto) }

    // ------------------------------------------------------------------ HU 31
    /**
     * Guarda un producto como favorito.
     *
     * Si el backend responde 2xx pero sin marcar el favorito, se devuelve un
     * error con el mismo texto que mostraba la interfaz antes
     * ("Error 200: no se pudo guardar el favorito.").
     */
    suspend fun agregarFavorito(idProducto: Long): ResultadoApi<FavoritoResponse> {
        val resultado = ejecutarPeticion(
            excepcion = { "No hay conexión para guardar el favorito." },
            porCodigo = { "Error $it: no se pudo guardar el favorito." }
        ) { api.agregarFavorito(idProducto) }

        if (resultado is ResultadoApi.Exito && resultado.valor.favorito != true) {
            return ResultadoApi.Error(
                codigo = 200,
                mensaje = "Error 200: no se pudo guardar el favorito.",
                temporal = false
            )
        }
        return resultado
    }

    // ------------------------------------------------------- HU 32 / HU 33
    /**
     * Quita el favorito; solo se elimina la relación, no el producto.
     *
     * HU 32 - Eliminación de favoritos: al volver a la lista, el producto ya
     * no aparece entre los favoritos del cliente.
     *
     * El endpoint responde **200 con el cuerpo vacío** (el método del
     * backend es `void`, no `Response.noContent()`), por eso el resultado se
     * expresa con `Unit` y se acepta cualquier 2xx. El código 404 se
     * conserva para que la interfaz mantenga el aviso "El favorito ya no
     * existía; la lista fue actualizada.".
     */
    suspend fun quitarFavorito(idProducto: Long): ResultadoApi<Unit> {
        return try {
            val respuesta = api.quitarFavorito(idProducto)
            if (respuesta.isSuccessful) {
                ResultadoApi.Exito(Unit)
            } else {
                val codigo = respuesta.code()
                ResultadoApi.Error(
                    codigo = codigo,
                    mensaje = "Error $codigo: no se pudo quitar el favorito.",
                    temporal = codigo >= 500 || codigo == 408 || codigo == 429
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoApi.Error(
                codigo = null,
                mensaje = "No hay conexión para quitar el favorito.",
                temporal = true
            )
        }
    }
}
