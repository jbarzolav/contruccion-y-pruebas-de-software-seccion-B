package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Resena;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResenaRepository extends JpaRepository<Resena, Long> {
    List<Resena> findByPedidoIdOrderByFechaCreacionAsc(Long pedidoId);
    List<Resena> findByProductoIdAndDestinoTipoOrderByFechaCreacionDescIdDesc(Long productoId, String destinoTipo);
    Optional<Resena> findByPedidoIdAndDestinoTipo(Long pedidoId, String destinoTipo);
    boolean existsByPedidoIdAndDestinoTipo(Long pedidoId, String destinoTipo);
}
