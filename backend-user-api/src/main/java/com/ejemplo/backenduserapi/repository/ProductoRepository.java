package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByVendedorId(Long vendedorId);

    // HU 06 - Buscar productos disponibles por coincidencia de nombre
    List<Producto> findByNombreContainingIgnoreCaseAndEstado(
            String nombre, String estado);

    // HU 07 - Catálogo: listado por estado (el service solo pide DISPONIBLE)
    List<Producto> findByEstado(String estado);
}
