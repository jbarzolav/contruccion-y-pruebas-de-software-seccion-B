package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.PerfilVendedorRequest;
import com.ejemplo.backenduserapi.dto.PerfilVendedorResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.PerfilVendedorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 29 - Perfil comercial del vendedor autenticado.
 *
 * El interceptor exige el permiso GESTIONAR_PRODUCTOS (solo VENDEDOR) y
 * coloca la identidad en "usuarioSesion": no existe PATCH /vendedores/{id},
 * el usuario nunca puede elegir a otro vendedor.
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/vendedores/me/perfil")
public class PerfilVendedorController {

    private final PerfilVendedorService perfiles;

    public PerfilVendedorController(PerfilVendedorService perfiles) {
        this.perfiles = perfiles;
    }

    // HU 29 - GET: 200 con el perfil comercial (vacío si aún no se completó); 401/403 si no corresponde.
    @GetMapping
    public PerfilVendedorResponse obtener(@RequestAttribute("usuarioSesion") Usuario usuario) {
        return perfiles.obtener(usuario);
    }

    // HU 29 - PATCH: 200 con el perfil guardado; 400 datos inválidos; 401 sin sesión; 403 si no es VENDEDOR.
    @PatchMapping
    public PerfilVendedorResponse guardar(@RequestAttribute("usuarioSesion") Usuario usuario,
                                          @Valid @RequestBody PerfilVendedorRequest solicitud) {
        return perfiles.guardar(usuario, solicitud);
    }
}
