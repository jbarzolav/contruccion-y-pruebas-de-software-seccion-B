package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ActualizarPerfilRequest;
import com.ejemplo.backenduserapi.dto.PerfilUsuarioResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 27 - Datos del perfil del usuario autenticado.
 *
 * El interceptor de autorización exige la sesión y coloca la identidad en
 * "usuarioSesion": solo se devuelven los datos propios y nunca la contraseña
 * ni su hash.
 *
 * HU 28 - PATCH /api/usuarios/me actualiza únicamente los campos personales
 * habilitados del usuario de la sesión; el body no puede elegir otro usuario
 * ni modificar rol, permisos ni contraseña.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final PerfilService perfiles;

    public UsuarioController(PerfilService perfiles) {
        this.perfiles = perfiles;
    }

    // HU 27 - GET /api/usuarios/me: 200 con los datos de la sesión; 401 sin sesión.
    @GetMapping("/me")
    public PerfilUsuarioResponse perfil(@RequestAttribute("usuarioSesion") Usuario usuario) {
        return PerfilUsuarioResponse.de(usuario);
    }

    // HU 28 - PATCH /api/usuarios/me: 200 con el perfil actualizado; 400/401/409 según el caso.
    @PatchMapping("/me")
    public PerfilUsuarioResponse actualizarPerfil(@RequestAttribute("usuarioSesion") Usuario usuario,
                                                  @Valid @RequestBody ActualizarPerfilRequest solicitud) {
        return perfiles.actualizar(usuario, solicitud);
    }
}
