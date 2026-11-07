package com.ejemplo.backenduserapi.dto;

import java.math.BigDecimal;

/**
 * HU 57 - Producto destacado por unidades vendidas en el periodo.
 *
 * `unidadesVendidas` es la suma de cantidades de las líneas del producto en
 * pedidos con venta confirmada. `importeAtribuido` es el importe de esas
 * líneas (cantidad x precioUnitario registrado en la compra).
 */
public record ProductoVendidoResponse(Long idProducto,
                                       String nombre,
                                       String imagenUrl,
                                       Long unidadesVendidas,
                                       BigDecimal importeAtribuido) {
}
