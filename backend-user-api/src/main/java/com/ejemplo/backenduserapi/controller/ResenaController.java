package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ResenaEdicionRequest;
import com.ejemplo.backenduserapi.dto.ResenaResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.ExperienciaCompraService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HU 45 - Edición segura de reseñas propias. */
@RestController
@RequestMapping("/api/resenas")
@CrossOrigin(origins = "*")
public class ResenaController {
    private final ExperienciaCompraService experienciaCompraService;

    public ResenaController(ExperienciaCompraService experienciaCompraService) {
        this.experienciaCompraService = experienciaCompraService;
    }

    @PutMapping("/{idResena}")
    public ResponseEntity<ResenaResponse> editar(
            @PathVariable Long idResena,
            @Valid @RequestBody ResenaEdicionRequest request,
            HttpServletRequest httpRequest) {
        Usuario usuario = (Usuario) httpRequest.getAttribute("usuarioSesion");
        return ResponseEntity.ok(experienciaCompraService.editarResena(idResena, request, usuario));
    }
}
