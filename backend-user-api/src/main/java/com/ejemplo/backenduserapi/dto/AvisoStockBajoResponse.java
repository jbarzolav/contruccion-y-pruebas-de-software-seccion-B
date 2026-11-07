package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

/**
 * HU 58 - Aviso de stock bajo del vendedor.
 *
 * El aviso nace en el sistema de notificaciones existente (tipo STOCK_BAJO) y
 * esta vista lo enriquece con el stock actual y el umbral vigente para que el
 * vendedor pueda decidir la reposición desde el panel.
 */
public record AvisoStockBajoResponse(Long idNotificacion,
                                     Long idProducto,
                                     String nombre,
                                     String imagenUrl,
                                     Integer stockActual,
                                     Integer umbral,
                                     Boolean stockBajo,
                                     String estado,
                                     LocalDateTime fecha) {
}
