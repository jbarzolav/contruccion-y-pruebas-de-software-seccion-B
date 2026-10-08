package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}
