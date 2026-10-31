package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ResenaProductoPublicaResponse;
import com.ejemplo.backenduserapi.service.ResenaProductoPublicaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HU 44 - Consulta pública de reseñas por producto. */
@RestController
@RequestMapping("/api/productos")
@CrossOrigin(origins = "*")
public class ResenaProductoPublicaController {

    private final ResenaProductoPublicaService resenaProductoPublicaService;

    public ResenaProductoPublicaController(ResenaProductoPublicaService resenaProductoPublicaService) {
        this.resenaProductoPublicaService = resenaProductoPublicaService;
    }

    @GetMapping("/{productoId}/resenas")
    public ResponseEntity<List<ResenaProductoPublicaResponse>> listarPorProducto(
            @PathVariable Long productoId) {
        return ResponseEntity.ok(resenaProductoPublicaService.listarPorProducto(productoId));
    }
}
