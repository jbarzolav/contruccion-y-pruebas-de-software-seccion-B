package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.HistorialEstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** HU 49 - Historial de cambios de estado por pedido. */
public interface HistorialEstadoPedidoRepository extends JpaRepository<HistorialEstadoPedido, Long> {

    /** Cambios del pedido del más reciente al más antiguo. */
    List<HistorialEstadoPedido> findByPedidoIdOrderByFechaCambioDesc(Long pedidoId);
}
