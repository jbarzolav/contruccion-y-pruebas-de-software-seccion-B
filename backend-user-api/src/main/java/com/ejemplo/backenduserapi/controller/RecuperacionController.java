package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.RecuperacionRequest;
import com.ejemplo.backenduserapi.dto.RestablecerRequest;
import com.ejemplo.backenduserapi.service.RecuperacionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class RecuperacionController {
    private final RecuperacionService servicio;

    public RecuperacionController(RecuperacionService servicio) { this.servicio = servicio; }

    @PostMapping("/recuperacion")
    public Map<String, String> solicitar(@Valid @RequestBody RecuperacionRequest solicitud) {
        servicio.solicitar(solicitud.getCorreo());
        return Map.of("mensaje", RecuperacionService.MENSAJE_SOLICITUD);
    }

    @PostMapping("/restablecer")
    public Map<String, String> restablecer(@Valid @RequestBody RestablecerRequest solicitud) {
        servicio.restablecer(solicitud);
        return Map.of("mensaje", "Contraseña actualizada correctamente.");
    }
}
