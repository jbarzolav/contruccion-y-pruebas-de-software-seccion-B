package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.ItemCarrito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemCarritoRepository extends JpaRepository<ItemCarrito, Long> {
    // HU 23 - Propiedad del ítem sin cargar relaciones diferidas.
    boolean existsByIdAndCarritoClienteId(Long id, Long clienteId);

    List<ItemCarrito> findByCarritoId(Long carritoId);
}
