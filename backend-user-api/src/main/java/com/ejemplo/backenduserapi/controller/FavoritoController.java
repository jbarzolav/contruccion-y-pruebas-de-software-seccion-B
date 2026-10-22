package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.FavoritoResponse;
import com.ejemplo.backenduserapi.dto.FavoritoProductoResponse;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.FavoritoService;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU31/HU32/HU33 - Favoritos del cliente autenticado. */
@RestController
@RequestMapping("/api/usuarios/me/favoritos")
@CrossOrigin(origins = "*")
public class FavoritoController {
    private final FavoritoService favoritos;

    public FavoritoController(FavoritoService favoritos) { this.favoritos = favoritos; }

    @GetMapping
    public List<FavoritoProductoResponse> listar(@RequestAttribute("usuarioSesion") Usuario usuario) {
        return favoritos.listarRespuestas(usuario);
    }

    /** HU35 - Consulta si un producto pertenece a los favoritos del cliente autenticado. */
    @GetMapping("/{idProducto}/estado")
    public FavoritoResponse consultarEstado(@RequestAttribute("usuarioSesion") Usuario usuario,
            @PathVariable Long idProducto) {
        return favoritos.consultarEstado(usuario, idProducto);
    }

    @PostMapping("/{idProducto}")
    public FavoritoResponse agregar(@RequestAttribute("usuarioSesion") Usuario usuario,
            @PathVariable Long idProducto) {
        return favoritos.agregar(usuario, idProducto);
    }

    @DeleteMapping("/{idProducto}")
    public void eliminar(@RequestAttribute("usuarioSesion") Usuario usuario,
            @PathVariable Long idProducto) {
        favoritos.eliminar(usuario, idProducto);
    }
}
