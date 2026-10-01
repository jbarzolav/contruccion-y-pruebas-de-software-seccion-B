package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
