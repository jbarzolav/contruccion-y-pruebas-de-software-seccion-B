package com.ejemplo.backenduserapi.dto;

import java.util.List;

public record PlanPremiumResponse(
        String nombre,
        String descripcion,
        List<String> beneficios
) {
}
