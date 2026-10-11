package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.VendedorPlan;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * HU 19 - Persistencia del plan Premium por vendedor (tabla vendedores_plan).
 */
public interface VendedorPlanRepository extends JpaRepository<VendedorPlan, Long> {
}
