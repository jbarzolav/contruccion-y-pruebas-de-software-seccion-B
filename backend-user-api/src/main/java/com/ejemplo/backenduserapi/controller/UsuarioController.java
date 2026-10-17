package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.UsuarioRolResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 27 - Datos del perfil del usuario autenticado.
 *
 * El interceptor de autorización exige la sesión y coloca la identidad en
 * "usuarioSesion": solo se devuelven los datos propios y nunca la contraseña
 * ni su hash.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/usuarios")
public class UsuarioController {

    // HU 27 - GET /api/usuarios/me: 200 con los datos de la sesión; 401 sin sesión.
    @GetMapping("/me")
    public UsuarioRolResponse perfil(@RequestAttribute("usuarioSesion") Usuario usuario) {
        return UsuarioRolResponse.de(usuario);
    }
}
