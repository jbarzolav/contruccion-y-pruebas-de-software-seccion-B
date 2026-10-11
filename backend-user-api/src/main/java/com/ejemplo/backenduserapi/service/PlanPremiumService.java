package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.PlanPremiumResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlanPremiumService {

    /**
     * HU 19 - Registro de vendedores con plan Premium activo.
     * Aún no existe autenticación real (convención del proyecto:
     * vendedorId = 1 fijo), por lo que el vendedor 1 nace Premium
     * y con él puede probarse el beneficio de destacar publicaciones.
     */
    private final Set<Long> vendedoresPremium = ConcurrentHashMap.newKeySet();

    public PlanPremiumService() {
        vendedoresPremium.add(1L);
    }

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

    /**
     * HU 19 - Indica si el vendedor cuenta con el beneficio Premium activo.
     */
    public boolean esVendedorPremium(Long vendedorId) {
        return vendedorId != null && vendedoresPremium.contains(vendedorId);
    }
}
