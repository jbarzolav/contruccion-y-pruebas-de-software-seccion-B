package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ProductoResumenResponse;
import com.ejemplo.backenduserapi.service.RecomendacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HU 20 - Recomendaciones relacionadas con las búsquedas.
 *
 * GET /api/recomendaciones?termino={texto}
 *   -> 200 con la lista de productos relacionados (puede ser vacía).
 * El término es opcional: si llega vacío o ausente se responde 200
 * con lista vacía; nunca 500. La lógica vive en RecomendacionService.
 */
@RestController
@RequestMapping("/api/recomendaciones")
@CrossOrigin(origins = "*")
public class RecomendacionesController {

    private final RecomendacionService recomendacionService;

    public RecomendacionesController(RecomendacionService recomendacionService) {
        this.recomendacionService = recomendacionService;
    }

    @GetMapping
    public ResponseEntity<List<ProductoResumenResponse>> recomendar(
            @RequestParam(value = "termino", required = false) String termino) {

        return ResponseEntity.ok(recomendacionService.recomendar(termino));
    }
}
