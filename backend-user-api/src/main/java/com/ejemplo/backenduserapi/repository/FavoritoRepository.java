package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Favorito;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** HU 31 - Contrato de almacenamiento para favoritos, separado del servicio. */
public interface FavoritoRepository extends JpaRepository<Favorito, Long> {
    boolean existsByUsuario_IdAndProducto_Id(Long usuarioId, Long productoId);
    List<Favorito> findByUsuario_IdOrderByIdDesc(Long usuarioId);
    Optional<Favorito> findByUsuario_IdAndProducto_Id(Long usuarioId, Long productoId);
}
