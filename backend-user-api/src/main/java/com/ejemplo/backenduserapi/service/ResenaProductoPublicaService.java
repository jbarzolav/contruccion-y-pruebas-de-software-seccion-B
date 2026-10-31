package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ResenaProductoPublicaResponse;
import com.ejemplo.backenduserapi.entity.Resena;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.repository.CalificacionRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.ejemplo.backenduserapi.repository.ResenaRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResenaProductoPublicaService {

    private static final String DESTINO_PRODUCTO = "PRODUCTO";

    private final ProductoRepository productos;
    private final ResenaRepository resenas;
    private final UsuarioRepository usuarios;
    private final CalificacionRepository calificaciones;

    public ResenaProductoPublicaService(ProductoRepository productos, ResenaRepository resenas,
                                       UsuarioRepository usuarios, CalificacionRepository calificaciones) {
        this.productos = productos;
        this.resenas = resenas;
        this.usuarios = usuarios;
        this.calificaciones = calificaciones;
    }

    @Transactional(readOnly = true)
    public List<ResenaProductoPublicaResponse> listarPorProducto(Long productoId) {
        if (productoId == null || productoId < 1) {
            throw new ExperienciaCompraException(HttpStatus.BAD_REQUEST,
                    "El identificador del producto no es válido.");
        }
        if (!productos.existsById(productoId)) {
            throw new ProductoNoEncontradoException("No se encontró el producto.");
        }

        return resenas.findByProductoIdAndDestinoTipoOrderByFechaCreacionDescIdDesc(
                        productoId, DESTINO_PRODUCTO)
                .stream()
                .map(this::respuestaPublica)
                .toList();
    }

    private ResenaProductoPublicaResponse respuestaPublica(Resena resena) {
        String nombreAutor = usuarios.findById(resena.getAutorId())
                .map(usuario -> usuario.getNombre() == null ? "" : usuario.getNombre().trim())
                .filter(nombre -> !nombre.isBlank())
                .orElse("Usuario");
        Integer calificacion = calificaciones.findByPedidoId(resena.getPedido().getId())
                .map(item -> item.getValor())
                .orElse(null);
        return new ResenaProductoPublicaResponse(resena.getId(), resena.getProductoId(),
                nombreAutor, calificacion, resena.getTexto(), resena.getFechaCreacion());
    }
}
