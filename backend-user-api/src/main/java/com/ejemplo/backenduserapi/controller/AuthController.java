package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.LoginRequest;
import com.ejemplo.backenduserapi.dto.LoginResponse;
import com.ejemplo.backenduserapi.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 21 - POST /api/auth/login
 *
 * 200: credenciales correctas (devuelve token opaco + usuario).
 * 400: validaciones Jakarta del LoginRequest.
 * 401: correo inexistente o contraseña incorrecta (mismo mensaje).
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest solicitud) {
        return ResponseEntity.ok(authService.login(solicitud));
    }
}
