package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.PaginaAuditoriaResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.AuditService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HU24 - Consulta administrativa paginada del historial de acciones. */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/admin/auditoria")
public class AdminAuditoriaController {
    private final AuditService auditoria;

    public AdminAuditoriaController(AuditService auditoria) { this.auditoria = auditoria; }

    @GetMapping
    public PaginaAuditoriaResponse consultar(
            @RequestAttribute("usuarioSesion") Usuario administrador,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditoria.consultar(administrador, page, size);
    }
}
