package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {
    Optional<Calificacion> findByPedidoId(Long pedidoId);
    boolean existsByPedidoId(Long pedidoId);

    long countByVendedorIdAndValorBetween(Long vendedorId, Integer minimo, Integer maximo);

    @Query("select avg(c.valor) from Calificacion c where c.vendedorId = :vendedorId "
            + "and c.valor between 1 and 5")
    Double calcularPromedioPorVendedor(@Param("vendedorId") Long vendedorId);

    @Query("select c.valor, count(c.id) from Calificacion c "
            + "where c.vendedorId = :vendedorId and c.valor between 1 and 5 "
            + "group by c.valor order by c.valor")
    java.util.List<Object[]> contarPorValorYVendedor(@Param("vendedorId") Long vendedorId);
}
