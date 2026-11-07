package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.DetallePedido;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {

    List<DetallePedido> findByPedidoId(Long pedidoId);

    // ------------------------------------------------------------------
    // HU 57 - Métricas de ventas del vendedor.
    //
    // Las agregaciones se resuelven en la base de datos: no se cargan los
    // pedidos en memoria y no hay consultas N+1. Todo el filtro se apoya en
    // la línea (detalle) para atribuir a cada vendedor únicamente el importe
    // de sus propios productos, incluso en pedidos compartidos.
    // ------------------------------------------------------------------

    /**
     * Suma de subtotales (cantidad x precioUnitario) de las líneas del
     * vendedor en pedidos con venta confirmada dentro del periodo.
     * Devuelve null cuando el vendedor no tuvo líneas en el periodo.
     */
    @Query("""
            SELECT SUM(d.cantidad * d.precioUnitario)
            FROM DetallePedido d
            WHERE d.producto.vendedorId = :vendedorId
              AND d.pedido.estado IN :estados
              AND d.pedido.fecha >= :desde
              AND d.pedido.fecha < :hasta
            """)
    BigDecimal sumarIngresosConfirmados(@Param("vendedorId") Long vendedorId,
                                        @Param("estados") List<String> estados,
                                        @Param("desde") LocalDateTime desde,
                                        @Param("hasta") LocalDateTime hasta);

    /**
     * Cantidad de pedidos DISTINTOS que incluyen productos del vendedor.
     * Un pedido con varios productos del mismo vendedor se cuenta una sola vez.
     */
    @Query("""
            SELECT COUNT(DISTINCT d.pedido.id)
            FROM DetallePedido d
            WHERE d.producto.vendedorId = :vendedorId
              AND d.pedido.estado IN :estados
              AND d.pedido.fecha >= :desde
              AND d.pedido.fecha < :hasta
            """)
    Long contarPedidosConfirmados(@Param("vendedorId") Long vendedorId,
                                  @Param("estados") List<String> estados,
                                  @Param("desde") LocalDateTime desde,
                                  @Param("hasta") LocalDateTime hasta);

    /**
     * Productos del vendedor ordenados por unidades vendidas.
     * Se agrupa por producto (no por línea) para no duplicar unidades cuando
     * el mismo producto aparece en varias líneas del mismo pedido.
     */
    @Query("""
            SELECT d.producto.id,
                   MAX(d.producto.nombre),
                   MAX(d.producto.imagenUrl),
                   SUM(d.cantidad),
                   SUM(d.cantidad * d.precioUnitario)
            FROM DetallePedido d
            WHERE d.producto.vendedorId = :vendedorId
              AND d.pedido.estado IN :estados
              AND d.pedido.fecha >= :desde
              AND d.pedido.fecha < :hasta
            GROUP BY d.producto.id
            ORDER BY SUM(d.cantidad) DESC, MAX(d.producto.nombre) ASC
            """)
    List<Object[]> productosMasVendidos(@Param("vendedorId") Long vendedorId,
                                        @Param("estados") List<String> estados,
                                        @Param("desde") LocalDateTime desde,
                                        @Param("hasta") LocalDateTime hasta,
                                        Pageable pageable);
}
