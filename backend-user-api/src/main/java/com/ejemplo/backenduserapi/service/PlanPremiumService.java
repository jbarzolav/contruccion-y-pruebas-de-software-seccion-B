package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.PlanPremiumResponse;
import com.ejemplo.backenduserapi.entity.VendedorPlan;
import com.ejemplo.backenduserapi.repository.VendedorPlanRepository;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class PlanPremiumService {

    /**
     * HU 19 - Estado del plan Premium, persistido en la BD
     * (tabla vendedores_plan; nada simulado: aquí se consulta).
     *
     * Aún no existe autenticación real (convención del proyecto:
     * vendedorId = 1 fijo), por lo que el arranque siembra el plan
     * ACTIVO del vendedor 1 para poder probar el beneficio.
     */
    private final VendedorPlanRepository vendedorPlanRepository;

    public PlanPremiumService(VendedorPlanRepository vendedorPlanRepository) {
        this.vendedorPlanRepository = vendedorPlanRepository;

        if (!vendedorPlanRepository.existsById(1L)) {
            VendedorPlan plan = new VendedorPlan(1L, true);
            plan.setFechaActivacion(Timestamp.from(Instant.now()));
            vendedorPlanRepository.save(plan);
        }
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
     * HU 19 - Verifica en la BD que el vendedor tenga el plan Premium activo.
     */
    public boolean esVendedorPremium(Long vendedorId) {
        return vendedorId != null
                && vendedorPlanRepository.findById(vendedorId)
                .map(VendedorPlan::isPremiumActivo)
                .orElse(false);
    }
}
