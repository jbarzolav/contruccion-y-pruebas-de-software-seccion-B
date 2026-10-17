package com.ejemplo.backenduserapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.dto.UsuarioRolResponse;
import com.ejemplo.backenduserapi.dto.LoginRequest;
import com.ejemplo.backenduserapi.dto.LoginResponse;
import com.ejemplo.backenduserapi.dto.AuthMessageResponse;
import com.ejemplo.backenduserapi.dto.RegistroRequest;
import com.ejemplo.backenduserapi.service.AuthService;
import com.ejemplo.backenduserapi.service.RegistroService;
import com.ejemplo.backenduserapi.service.SesionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

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
    private final SesionService sesionService;
    private final RegistroService registroService;

    public AuthController(AuthService authService, SesionService sesionService,
                          RegistroService registroService) {
        this.authService = authService;
        this.sesionService = sesionService;
        this.registroService = registroService;
    }

    // HU 23 - Rol actual de la sesión, sin contraseña ni hash.
    @GetMapping("/me")
    public UsuarioRolResponse sesion(
            @RequestAttribute("usuarioSesion") Usuario usuario) {
        return UsuarioRolResponse.de(usuario);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest solicitud) {
        return ResponseEntity.ok(authService.login(solicitud));
    }

    /**
     * HU 26 - Registro de cuenta.
     *
     * 201: cuenta creada (siempre CLIENTE, sin token automático).
     * 400: validaciones del RegistroRequest, contraseñas distintas o > 72 bytes.
     * 409: el correo ya está registrado.
     */
    @PostMapping("/registro")
    public ResponseEntity<AuthMessageResponse> registro(@Valid @RequestBody RegistroRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registroService.registrar(solicitud));
    }

    /** HU25 - Revoca exclusivamente el token Bearer que presenta la petición. */
    @PostMapping("/logout")
    public ResponseEntity<AuthMessageResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(sesionService.cerrar(authorization));
    }
}
