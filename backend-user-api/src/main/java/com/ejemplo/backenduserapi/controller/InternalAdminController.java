package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.AsignarRolRequest;
import com.ejemplo.backenduserapi.dto.UsuarioRolResponse;
import com.ejemplo.backenduserapi.service.AutorizacionService;
import com.ejemplo.backenduserapi.service.RolService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Integración servicio-a-servicio exclusiva de Django; no forma parte de la API pública. */
@RestController
@RequestMapping("/_internal/admin")
public class InternalAdminController {
    private final String apiKey;
    private final AutorizacionService autorizacion;
    private final RolService roles;

    public InternalAdminController(
            @Value("${app.internal.admin-api-key:}") String apiKey,
            AutorizacionService autorizacion,
            RolService roles) {
        this.apiKey = apiKey;
        this.autorizacion = autorizacion;
        this.roles = roles;
    }

    @PatchMapping("/usuarios/{idUsuario}/rol")
    public ResponseEntity<UsuarioRolResponse> asignarRol(
            @PathVariable Long idUsuario,
            @RequestHeader(value = "X-Internal-Admin-Key", required = false) String presentedKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AsignarRolRequest request) {
        if (apiKey.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        if (presentedKey == null || !MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8), presentedKey.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var administrator = autorizacion.autenticar(authorization);
        return ResponseEntity.ok(roles.asignar(administrator, idUsuario, request.getRol()));
    }
}
