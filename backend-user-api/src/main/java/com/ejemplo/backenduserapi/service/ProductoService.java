package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ProductoRequest;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Transactional
    public Producto registrar(ProductoRequest request) {
        Producto producto = new Producto();
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecio(request.getPrecio());
        producto.setStock(request.getStock());
        producto.setCategoria(request.getCategoria());
        producto.setEstado(request.getEstado());
        producto.setImagenUrl(request.getImagenUrl());
        producto.setVendedorId(request.getVendedorId());

        return productoRepository.save(producto);
    }

    @Transactional(readOnly = true)
    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con id " + id));
    }
}
