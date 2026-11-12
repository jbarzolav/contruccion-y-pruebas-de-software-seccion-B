package com.ejemplo.publicarproducto.model.repository

import com.ejemplo.publicarproducto.model.CalificacionesVendedorResumenResponse
import com.ejemplo.publicarproducto.model.DestacarRequest
import com.ejemplo.publicarproducto.model.DisponibilidadProductoResponse
import com.ejemplo.publicarproducto.model.EstadoProductoRequest
import com.ejemplo.publicarproducto.model.PlanPremiumResponse
import com.ejemplo.publicarproducto.model.ProductoRequest
import com.ejemplo.publicarproducto.model.ProductoResponse
import com.ejemplo.publicarproducto.model.ResenaProductoPublicaResponse
import com.ejemplo.publicarproducto.model.remote.ProductoApi
import com.ejemplo.publicarproducto.model.remote.ResultadoApi
import com.ejemplo.publicarproducto.model.remote.ejecutarPeticion
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Repositorio de productos (HU 01, 02, 03, 04, 05, 06, 07, 09, 10, 18, 19,
 * 20, 43 y 44).
 *
 * Es el ÚNICO punto de la app que habla con los endpoints de producto: las
 * pantallas y los ViewModels ya no conocen `ProductoApi` ni construyen
 * cuerpos `multipart`.
 *
 * Reglas conservadas de la implementación anterior:
 * - El `vendedorId` se resuelve desde la sesión (nunca lo decide la
 *   pantalla). Si no hay sesión, se conserva el mismo error que antes:
 *   "Debes iniciar sesión.".
 * - Los textos de error son exactamente los que mostraba la interfaz antes
 *   de la refactorización, incluido el detalle de la excepción.
 * - El multipart mantiene los mismos campos (`file` y `vendedorId`), el
 *   mismo nombre de archivo y la misma regla de `contentType`.
 * - No toca Room, ni la cola de sincronización, ni WorkManager: las
 *   operaciones de producto siguen siendo en línea, igual que antes.
 */
class ProductosRepositorio(
    private val api: ProductoApi,
    private val idVendedor: () -> Long,
) {

    companion object {
        /**
         * El backend exige `imagenUrl` al crear el producto; este valor se
         * reemplaza por la ruta real (/imagenes/...) apenas se sube el archivo.
         */
        private const val IMAGEN_PENDIENTE = "https://via.placeholder.com/300"

        /** Texto de red que ya mostraban las pantallas de producto. */
        private const val SIN_CONEXION = "No hay conexión con el backend (10.0.2.2:9000): "
    }

    /** Identidad del vendedor autenticado (misma regla que antes). */
    private fun vendedorId(): Long =
        requireNotNull(idVendedor().takeIf { it > 0 }) { "Debes iniciar sesión." }

    /** Mensaje por código HTTP compartido por edición y publicación. */
    private fun mensajePorCodigo(codigo: Int, sufijo: String): String = when (codigo) {
        404 -> "El producto no existe (HTTP 404)."
        403 -> "No tienes permiso sobre este producto (HTTP 403)."
        else -> "Error $codigo: $sufijo."
    }

    // ------------------------------------------------------------------ HU 05
    /** Publicaciones propias del vendedor autenticado. */
    suspend fun listarMisProductos(): ResultadoApi<List<ProductoResponse>> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { "Error $it: no se pudieron cargar los productos." },
            vacio = { emptyList() }
        ) { api.obtenerMisProductos(vendedorId()) }

    // ------------------------------------------------------------------ HU 19
    /** Activa o desactiva el destacado (plan Premium activo). */
    suspend fun gestionarDestacado(
        idProducto: Long,
        destacado: Boolean,
    ): ResultadoApi<ProductoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { "Error $it: no se pudo actualizar el destacado." }
        ) { api.gestionarDestacado(idProducto, DestacarRequest(destacado), vendedorId()) }

    // ------------------------------------------------------------------ HU 03
    /**
     * Carga de un producto para el formulario de edición.
     *
     * Este endpoint lo usaban dos pantallas con textos distintos: aquí se
     * conserva el de edición (lee `messages[]` y distingue 404 y 403).
     */
    suspend fun obtenerProducto(idProducto: Long): ResultadoApi<ProductoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { codigo ->
                mensajePorCodigo(codigo, "no se pudo cargar el producto")
            },
            leerMensajes = { true }
        ) { api.obtenerProducto(idProducto) }

    /**
     * Carga de un producto para la pantalla de detalle.
     *
     * Mismo endpoint que [obtenerProducto] pero con los textos que mostraba
     * el detalle: aquí no se leen los `messages[]` del backend.
     */
    suspend fun obtenerDetalleProducto(idProducto: Long): ResultadoApi<ProductoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { codigo ->
                when (codigo) {
                    404 -> "Producto no encontrado (id $idProducto)."
                    else -> "Error $codigo: no se pudo cargar el detalle."
                }
            }
        ) { api.obtenerProducto(idProducto) }

    // ------------------------------------------------------------------ HU 10
    /** Disponibilidad y stock vigente del producto. */
    suspend fun obtenerDisponibilidad(
        idProducto: Long,
    ): ResultadoApi<DisponibilidadProductoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { codigo ->
                when (codigo) {
                    404 -> "Producto no encontrado (id $idProducto)."
                    else -> "Error $codigo: no se pudo cargar el detalle."
                }
            }
        ) { api.obtenerDisponibilidadProducto(idProducto) }

    // -------------------------------------------------------------- HU 06 / 09
    /** Búsqueda por nombre con criterio de orden opcional. */
    suspend fun buscar(
        nombre: String,
        sort: String? = null,
    ): ResultadoApi<List<ProductoResponse>> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { "Error $it: no se pudo realizar la búsqueda." },
            vacio = { emptyList() }
        ) { api.buscarProductosPorNombre(nombre = nombre, sort = sort) }

    // ------------------------------------------------------------------ HU 07
    /** Catálogo público (solo productos DISPONIBLE). */
    suspend fun listarCatalogo(): ResultadoApi<List<ProductoResponse>> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { "Error $it: no se pudo cargar el catálogo." },
            vacio = { emptyList() }
        ) { api.listarCatalogo() }

    // ------------------------------------------------------------------ HU 20
    /** Recomendaciones relacionadas con el término buscado. */
    suspend fun listarRecomendaciones(
        termino: String,
    ): ResultadoApi<List<ProductoResponse>> =
        ejecutarPeticion(
            excepcion = { e ->
                "No se pudieron cargar las recomendaciones (10.0.2.2:9000): ${e.message}"
            },
            porCodigo = { "Error $it: no se pudieron cargar las recomendaciones." },
            vacio = { emptyList() }
        ) { api.obtenerRecomendaciones(termino) }

    // ------------------------------------------------------------------ HU 18
    /** Plan Premium vigente. La selección en pantalla no activa pagos. */
    suspend fun consultarPlanPremium(): ResultadoApi<PlanPremiumResponse> =
        ejecutarPeticion(
            excepcion = { "No hay conexión con el backend. Verifica que Spring Boot esté encendido." },
            porCodigo = { "No se pudo consultar el plan Premium. Error HTTP $it." }
        ) { api.consultarPlanPremium() }

    // ------------------------------------------------------------------ HU 43
    /** Resumen de calificaciones del vendedor del producto. */
    suspend fun resumenCalificacionesVendedor(
        vendedorId: Long,
    ): ResultadoApi<CalificacionesVendedorResumenResponse> =
        ejecutarPeticion(
            excepcion = { "No se pudieron cargar las calificaciones del vendedor." },
            porCodigo = { "No se pudieron cargar las calificaciones del vendedor." }
        ) { api.obtenerResumenCalificacionesVendedor(vendedorId) }

    // ------------------------------------------------------------------ HU 44
    /** Reseñas públicas del producto. */
    suspend fun listarResenasProducto(
        idProducto: Long,
    ): ResultadoApi<List<ResenaProductoPublicaResponse>> =
        ejecutarPeticion(
            excepcion = { "No se pudieron cargar las reseñas del producto." },
            porCodigo = { "No se pudieron cargar las reseñas del producto." },
            vacio = { emptyList() }
        ) { api.obtenerResenasProducto(idProducto) }

    // ------------------------------------------------------------------ HU 01
    /**
     * Registra el producto. El repositorio completa el `vendedorId` (sesión) y
     * el `imagenUrl` pendiente: el ViewModel no conoce esos valores internos.
     */
    suspend fun crearProducto(request: ProductoRequest): ResultadoApi<ProductoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { codigo ->
                mensajePorCodigo(codigo, "no se pudo registrar el producto")
            },
            leerMensajes = { true }
        ) {
            api.crearProducto(
                request.copy(
                    vendedorId = vendedorId(),
                    imagenUrl = request.imagenUrl.ifBlank { IMAGEN_PENDIENTE }
                )
            )
        }

    // ------------------------------------------------------------------ HU 02
    /**
     * Asocia la imagen real del producto (`multipart/form-data`).
     *
     * El repositorio arma el `MultipartBody.Part` con los mismos campos que
     * antes (`file` y `vendedorId`), el mismo nombre de archivo y la misma
     * regla de `contentType`.
     */
    suspend fun subirImagen(
        idProducto: Long,
        bytes: ByteArray,
        nombreArchivo: String?,
        mime: String,
    ): ResultadoApi<ProductoResponse> {
        val tipo = if (mime.startsWith("image/")) mime else "application/octet-stream"
        val nombre = nombreArchivo ?: ("producto." + if (mime == "image/png") "png" else "jpg")
        val parteArchivo = MultipartBody.Part.createFormData(
            "file",
            nombre,
            bytes.toRequestBody(tipo.toMediaTypeOrNull())
        )
        return ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { codigo ->
                mensajePorCodigo(
                    codigo,
                    "el producto fue creado, pero no se pudo subir la imagen"
                )
            },
            leerMensajes = { true }
        ) {            api.agregarImagen(
                idProducto,
                parteArchivo,
                vendedorId().toString().toRequestBody("text/plain".toMediaTypeOrNull())
            )
        }
    }

    // ------------------------------------------------------------------ HU 03
    /**
     * Guarda los cambios de una publicación.
     *
     * `vendedorIdPropietario` es el dueño de la publicación ya cargada; si no
     * se conoce, se usa el de la sesión (misma regla que antes).
     *
     * La `imagenUrl` se normaliza igual que en el alta: el backend la exige
     * siempre en el cuerpo (`@NotBlank`) aunque al editar NO la modifica
     * (solo la cambia la subida de imagen por multipart). Sin esta
     * normalización, editar una publicación cuya imagen no se conoce
     * devolvía HTTP 400 "La imagen es obligatoria" y el guardado fallaba.
     */
    suspend fun actualizarProducto(
        idProducto: Long,
        request: ProductoRequest,
        vendedorIdPropietario: Long? = null,
    ): ResultadoApi<ProductoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { codigo ->
                mensajePorCodigo(codigo, "no se pudo actualizar el producto")
            },
            leerMensajes = { true }
        ) {
            val vendedor = vendedorIdPropietario ?: vendedorId()
            api.actualizarProducto(
                idProducto,
                request.copy(
                    vendedorId = vendedor,
                    imagenUrl = request.imagenUrl.ifBlank { IMAGEN_PENDIENTE }
                ),
                vendedor
            )
        }

    // ------------------------------------------------------------------ HU 04
    /** Baja lógica de la publicación (estado RETIRADO). */
    suspend fun cambiarEstado(
        idProducto: Long,
        request: EstadoProductoRequest,
        vendedorIdPropietario: Long? = null,
    ): ResultadoApi<ProductoResponse> =
        ejecutarPeticion(
            excepcion = { e -> "$SIN_CONEXION${e.message}" },
            porCodigo = { codigo ->
                mensajePorCodigo(codigo, "no se pudo retirar la publicación")
            },
            leerMensajes = { true }
        ) {
            val vendedor = vendedorIdPropietario ?: vendedorId()
            api.cambiarEstado(idProducto, request, vendedor)
        }
}
