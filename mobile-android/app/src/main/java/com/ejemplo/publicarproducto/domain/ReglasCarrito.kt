package com.ejemplo.publicarproducto.domain

/**
 * Reglas del carrito de compras (HU 12).
 *
 * Antes vivían dentro de `CarritoViewModel`. Ahora están en un objeto de
 * lógica pura para poder probarlas en la JVM (`src/test`) sin emulador.
 */
object ReglasCarrito {

    /** Cantidad mínima de un ítem: nunca se puede bajar de 1. */
    const val CANTIDAD_MINIMA = 1

    /**
     * Calcula la cantidad resultante de pulsar `+` o `-`.
     *
     * @return la nueva cantidad, o `null` cuando el cambio no es válido
     *         (por ejemplo, bajar de 1).
     */
    fun nuevaCantidad(cantidadActual: Int, delta: Int): Int? {
        val nueva = cantidadActual + delta
        return if (nueva < CANTIDAD_MINIMA) null else nueva
    }
}
