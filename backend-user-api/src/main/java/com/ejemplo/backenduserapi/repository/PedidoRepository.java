package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByClienteIdOrderByFechaDesc(Long clienteId);

    // HU 48 - Busca pedidos que incluyen publicaciones del vendedor autenticado.
    @Query("select distinct p from Pedido p join p.detalles d where d.producto.vendedorId = :vendedorId order by p.fecha desc")
    List<Pedido> findPedidosConProductosDeVendedor(@Param("vendedorId") Long vendedorId);
}
