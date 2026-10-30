package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {
    Optional<Calificacion> findByPedidoId(Long pedidoId);
    boolean existsByPedidoId(Long pedidoId);
}
