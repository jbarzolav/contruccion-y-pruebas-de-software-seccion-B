package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.PlanPremiumResponse;
import com.ejemplo.backenduserapi.service.PlanPremiumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planes")
@CrossOrigin(origins = "*")
public class PlanPremiumController {

    private final PlanPremiumService planPremiumService;

    public PlanPremiumController(PlanPremiumService planPremiumService) {
        this.planPremiumService = planPremiumService;
    }

    // HU 18 - Consultar información y beneficios del plan Premium.
    @GetMapping("/premium")
    public ResponseEntity<PlanPremiumResponse> consultarPremium() {
        return ResponseEntity.ok(planPremiumService.consultarBeneficios());
    }
}
