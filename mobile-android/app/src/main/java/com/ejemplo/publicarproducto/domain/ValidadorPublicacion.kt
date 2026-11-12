package com.ejemplo.publicarproducto.domain

/**
 * Reglas de validación de la publicación de productos (HU 01 y HU 03).
 *
 * Antes vivían duplicadas dentro de `PublicarProductoViewModel` y
 * `EditarProductoViewModel`. Ahora están en un único objeto de lógica pura:
 * las pantallas y los ViewModels solo pintan los errores, y las reglas se
 * pueden probar en la JVM (`src/test`) sin emulador.
 *
 * Los mensajes son EXACTAMENTE los que ya mostraba la aplicación.
 */
object ValidadorPublicacion {

    /** Tipos MIME admitidos para la imagen del producto (HU 02). */
    private val TIPOS_PERMITIDOS = setOf("image/jpeg", "image/jpg", "image/png")

    /**
     * Valida el formulario de publicación o de edición.
     *
     * @param requiereImagen `true` en publicación (HU 01/HU 02) y `false` en
     *        edición (HU 03), donde la imagen no se vuelve a subir.
     * @return mapa de campo -> mensaje; vacío cuando el formulario es válido.
     */
    fun validar(
        nombre: String,
        descripcion: String,
        precio: String,
        stock: String,
        categoria: String,
        requiereImagen: Boolean = false,
        imagenPresente: Boolean = false,
    ): Map<String, String> {
        val errores = mutableMapOf<String, String>()

        if (nombre.isBlank()) {
            errores["nombre"] = "El nombre es obligatorio"
        }

        if (descripcion.isBlank()) {
            errores["descripcion"] = "La descripción es obligatoria"
        }

        val precioValor = precio.toDoubleOrNull()

        when {
            precio.isBlank() ->
                errores["precio"] = "El precio es obligatorio"

            precioValor == null ->
                errores["precio"] = "El precio no es un número válido"

            precioValor <= 0 ->
                errores["precio"] = "El precio debe ser mayor a 0"
        }

        val stockValor = stock.toIntOrNull()

        if (stock.isBlank()) {
            errores["stock"] = "El stock es obligatorio"
        } else if (stockValor == null) {
            errores["stock"] = "El stock no es un número válido"
        } else if (stockValor < 0) {
            errores["stock"] = "El stock no puede ser negativo"
        }

        if (categoria.isBlank()) {
            errores["categoria"] = "La categoría es obligatoria"
        }

        if (requiereImagen && !imagenPresente) {
            errores["imagen"] = "La imagen es obligatoria"
        }

        return errores
    }

    /** Extensiones de imagen admitidas (`.jpg`, `.jpeg`, `.png`). */
    fun extensionPermitida(nombre: String): Boolean {
        val minusc = nombre.lowercase()
        return minusc.endsWith(".jpg") || minusc.endsWith(".jpeg") || minusc.endsWith(".png")
    }

    /** Tipo MIME admitido; un MIME vacío se acepta (lo decide la extensión). */
    fun mimePermitido(mime: String): Boolean {
        val minusc = mime.lowercase()
        return minusc.isBlank() || minusc in TIPOS_PERMITIDOS
    }
}
