package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.PlanPremiumResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlanPremiumService {

    public PlanPremiumResponse consultarBeneficios() {
        return new PlanPremiumResponse(
                "Plan Premium",
                "Plan orientado a vendedores que desean mejorar la visibilidad de sus productos en PulgaTec.",
                List.of(
                        "Publicaciones destacadas",
                        "Mayor visibilidad de los productos",
                        "Herramientas adicionales para vendedores"
                )
        );
    }
}
