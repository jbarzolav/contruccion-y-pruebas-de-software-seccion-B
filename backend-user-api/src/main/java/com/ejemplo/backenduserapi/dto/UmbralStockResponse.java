package com.ejemplo.backenduserapi.dto;

import java.time.LocalDateTime;

/**
 * HU 58 - Umbral de stock de una publicación propia del vendedor.
 *
 * `umbral` llega en null cuando el vendedor todavía no configuró umbral para
 * esa publicación (estado "sin umbral configurado", no un error).
 * `stockBajo` indica que el stock actual ya está en o por debajo del umbral.
 * `alertaNotificada` indica que el episodio de stock bajo actual ya generó un
 * aviso: mientras siga en true no se repiten avisos.
 */
public record UmbralStockResponse(Long idProducto,
                                  String nombre,
                                  String imagenUrl,
                                  Integer stock,
                                  Integer umbral,
                                  Boolean stockBajo,
                                  Boolean alertaNotificada,
                                  LocalDateTime fechaActualizacion) {
}
