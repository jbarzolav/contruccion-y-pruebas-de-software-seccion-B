package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.FavoritoResponse;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.FavoritoService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU 31 - Consulta y alta de favoritos del cliente autenticado. */
@RestController
@RequestMapping("/api/usuarios/me/favoritos")
public class FavoritoController {
    private final FavoritoService favoritos;

    public FavoritoController(FavoritoService favoritos) { this.favoritos = favoritos; }

    @GetMapping
    public List<Producto> listar(@RequestAttribute("usuarioSesion") Usuario usuario) {
        return favoritos.listar(usuario);
    }

    @PostMapping("/{idProducto}")
    public FavoritoResponse agregar(@RequestAttribute("usuarioSesion") Usuario usuario,
            @PathVariable Long idProducto) {
        return favoritos.agregar(usuario, idProducto);
    }
}
