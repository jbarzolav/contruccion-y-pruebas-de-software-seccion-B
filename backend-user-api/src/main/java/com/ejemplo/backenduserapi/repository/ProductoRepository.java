package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    // Serializa la creación/reutilización de conversaciones sobre un producto.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Producto p where p.id = :id")
    java.util.Optional<Producto> buscarParaConsulta(@org.springframework.data.repository.query.Param("id") Long id);
    List<Producto> findByVendedorId(Long vendedorId);

    // HU 06 - Buscar productos disponibles por coincidencia de nombre
    List<Producto> findByNombreContainingIgnoreCaseAndEstado(
            String nombre, String estado);

    // HU 07 - Catálogo: listado por estado (el service solo pide DISPONIBLE)
    List<Producto> findByEstado(String estado);

    // HU 20 - Recomendaciones: coincidencia de categoría (solo DISPONIBLE)
    List<Producto> findByCategoriaContainingIgnoreCaseAndEstado(
            String categoria, String estado);
}
