package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import java.util.List;
import java.util.Optional;
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    // Serializa la creación/reutilización de conversaciones sobre un producto.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Producto p where p.id = :id")
    java.util.Optional<Producto> buscarParaConsulta(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * HU 58 - Lee la publicación con bloqueo de escritura y refrescando la
     * caché de primer nivel: dos ventas simultaneas no pueden descontar stock
     * sobre una copia obsoleta ni generar dos avisos del mismo cruce.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.cache.storeMode", value = "REFRESH"))
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> buscarParaActualizar(@Param("id") Long id);

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
