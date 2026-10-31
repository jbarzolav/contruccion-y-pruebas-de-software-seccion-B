package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.CalificacionesVendedorResumenResponse;
import com.ejemplo.backenduserapi.service.CalificacionPublicaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU 43 - Consulta pública del resumen de calificaciones de un vendedor. */
@RestController
@RequestMapping("/api/vendedores")
@CrossOrigin(origins = "*")
public class CalificacionPublicaController {

    private final CalificacionPublicaService calificacionPublicaService;

    public CalificacionPublicaController(CalificacionPublicaService calificacionPublicaService) {
        this.calificacionPublicaService = calificacionPublicaService;
    }

    @GetMapping("/{vendedorId}/calificaciones/resumen")
    public ResponseEntity<CalificacionesVendedorResumenResponse> obtenerResumen(
            @PathVariable Long vendedorId) {
        return ResponseEntity.ok(calificacionPublicaService.obtenerResumen(vendedorId));
    }
}
