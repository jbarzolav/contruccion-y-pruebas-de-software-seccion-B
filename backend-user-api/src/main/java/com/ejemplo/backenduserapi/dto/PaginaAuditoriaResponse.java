package com.ejemplo.backenduserapi.dto;

import java.util.List;

public record PaginaAuditoriaResponse(List<RegistroAuditoriaResponse> contenido, int pagina,
        int tamano, long totalElementos, int totalPaginas) { }
