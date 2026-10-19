package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.PerfilUsuarioResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.FotoPerfilService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** HU 30 - Actualiza la foto únicamente del usuario de la sesión. */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/usuarios/me/foto")
public class FotoPerfilController {
    private final FotoPerfilService fotos;

    public FotoPerfilController(FotoPerfilService fotos) { this.fotos = fotos; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PerfilUsuarioResponse subir(@RequestAttribute("usuarioSesion") Usuario usuario,
            @RequestPart("file") MultipartFile archivo) {
        return fotos.guardar(usuario, archivo);
    }
}
