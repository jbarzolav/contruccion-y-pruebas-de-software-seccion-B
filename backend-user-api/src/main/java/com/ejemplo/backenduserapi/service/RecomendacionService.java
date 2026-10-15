package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ProductoResumenResponse;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU 20 - Recomendaciones relacionadas con las búsquedas del usuario.
 *
 * Recibe el término buscado y devuelve productos relacionados por
 * nombre o por categoría, únicamente en estado DISPONIBLE (se excluyen
 * RETIRADO, INACTIVO y AGOTADO) y sin productos repetidos.
 *
 * La consulta vive en el Repository; el Controller solo la invoca.
 */
@Service
public class RecomendacionService {

    private static final String ESTADO_DISPONIBLE = "DISPONIBLE";

    private final ProductoRepository productoRepository;

    public RecomendacionService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    // ------------------------------------------------------------------
    // HU 20 - Recomendaciones por término de búsqueda.
    // Término vacío o nulo -> lista vacía con 200, nunca error 500.
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<ProductoResumenResponse> recomendar(String termino) {

        if (termino == null || termino.isBlank()) {
            return List.of();
        }

        String criterio = termino.trim();

        // LinkedHashMap conserva el orden e ignora claves repetidas:
        // un producto puede coincidir por nombre Y por categoría a la vez.
        Map<Long, Producto> relacionados = new LinkedHashMap<>();

        productoRepository
                .findByNombreContainingIgnoreCaseAndEstado(criterio, ESTADO_DISPONIBLE)
                .forEach(producto -> relacionados.putIfAbsent(producto.getId(), producto));

        productoRepository
                .findByCategoriaContainingIgnoreCaseAndEstado(criterio, ESTADO_DISPONIBLE)
                .forEach(producto -> relacionados.putIfAbsent(producto.getId(), producto));

        return relacionados.values().stream()
                .map(this::toResumen)
                .toList();
    }

    // ============================================================
    // CONVERTIR PRODUCTO A DTO (mismo mapeo que el chatbot HU 16)
    // ============================================================
    private ProductoResumenResponse toResumen(Producto producto) {

        return new ProductoResumenResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getPrecio(),
                producto.getStock(),
                producto.getCategoria(),
                producto.getImagenUrl()
        );
    }
}
