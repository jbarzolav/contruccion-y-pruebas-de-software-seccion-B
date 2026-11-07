package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.UmbralStock;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UmbralStockRepository extends JpaRepository<UmbralStock, Long> {

    @Query("select u from UmbralStock u where u.producto.id = :idProducto")
    Optional<UmbralStock> findByProductoId(@Param("idProducto") Long idProducto);

    /**
     * HU 58 - Lee el umbral con bloqueo de escritura para que dos operaciones
     * simultáneas (dos ventas, por ejemplo) no puedan emitir dos avisos del
     * mismo cruce. El refresco de caché garantiza el valor vigente en BD.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.cache.storeMode", value = "REFRESH"))
    @Query("select u from UmbralStock u where u.producto.id = :idProducto")
    Optional<UmbralStock> buscarParaActualizar(@Param("idProducto") Long idProducto);

    /**
     * HU 58 - Umbrales del vendedor con su publicación cargada en la misma
     * consulta (sin N+1 al armar el panel).
     */
    @EntityGraph(attributePaths = "producto")
    @Query("select u from UmbralStock u where u.producto.vendedorId = :vendedorId order by u.producto.nombre asc")
    List<UmbralStock> findByVendedorId(@Param("vendedorId") Long vendedorId);
}
