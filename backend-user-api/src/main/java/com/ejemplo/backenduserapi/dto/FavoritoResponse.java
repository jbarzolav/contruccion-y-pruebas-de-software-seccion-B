package com.ejemplo.backenduserapi.dto;

/** HU 31 - Resultado de guardar un producto en favoritos. */
public record FavoritoResponse(Long productoId, boolean favorito) {}
