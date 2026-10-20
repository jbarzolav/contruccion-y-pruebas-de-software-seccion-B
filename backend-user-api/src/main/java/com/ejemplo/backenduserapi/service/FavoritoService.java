package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.FavoritoResponse;
import com.ejemplo.backenduserapi.dto.FavoritoProductoResponse;
import com.ejemplo.backenduserapi.entity.Favorito;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.exception.FavoritoNoEncontradoException;
import com.ejemplo.backenduserapi.repository.FavoritoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU 31 - Reglas de favoritos desacopladas del mecanismo de almacenamiento. */
@Service
public class FavoritoService {
    private final FavoritoRepository favoritos;
    private final ProductoRepository productos;

    public FavoritoService(FavoritoRepository favoritos, ProductoRepository productos) {
        this.favoritos = favoritos;
        this.productos = productos;
    }

    @Transactional
    public FavoritoResponse agregar(Usuario usuario, Long productoId) {
        Producto producto = productos.findById(productoId).orElseThrow(
                () -> new ProductoNoEncontradoException("Producto no encontrado con id " + productoId));
        if (!favoritos.existsByUsuario_IdAndProducto_Id(usuario.getId(), productoId)) {
            favoritos.save(new Favorito(usuario, producto));
        }
        return new FavoritoResponse(productoId, true);
    }

    @Transactional(readOnly = true)
    public List<Producto> listar(Usuario usuario) {
        return favoritos.findByUsuario_IdOrderByIdDesc(usuario.getId()).stream()
                .map(Favorito::getProducto)
                .toList();
    }

    /** HU32 - materializa el DTO mientras la relación lazy sigue abierta. */
    @Transactional(readOnly = true)
    public List<FavoritoProductoResponse> listarRespuestas(Usuario usuario) {
        return favoritos.findByUsuario_IdOrderByIdDesc(usuario.getId()).stream()
                .map(Favorito::getProducto)
                .map(FavoritoProductoResponse::desde)
                .toList();
    }

    /** HU33 - elimina solo la relación perteneciente al usuario autenticado. */
    @Transactional
    public void eliminar(Usuario usuario, Long productoId) {
        Favorito favorito = favoritos.findByUsuario_IdAndProducto_Id(usuario.getId(), productoId)
                .orElseThrow(() -> new FavoritoNoEncontradoException(
                        "El producto no está en los favoritos del usuario."));
        favoritos.delete(favorito);
    }
}
