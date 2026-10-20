package com.ejemplo.backenduserapi.dto;

import com.ejemplo.backenduserapi.entity.Producto;
import java.math.BigDecimal;

/** HU32 - Datos públicos necesarios para pintar un favorito. */
public record FavoritoProductoResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer stock,
        String categoria,
        String estado,
        String imagenUrl,
        boolean esDestacado) {

    public static FavoritoProductoResponse desde(Producto producto) {
        return new FavoritoProductoResponse(producto.getId(), producto.getNombre(), producto.getDescripcion(),
                producto.getPrecio(), producto.getStock(), producto.getCategoria(), producto.getEstado(),
                producto.getImagenUrl(), producto.isEsDestacado());
    }
}
