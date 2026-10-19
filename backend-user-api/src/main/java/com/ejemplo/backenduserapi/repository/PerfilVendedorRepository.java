package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.PerfilVendedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PerfilVendedorRepository extends JpaRepository<PerfilVendedor, Long> {

    /** HU 29 - Un vendedor tiene a lo sumo un perfil (FK única). */
    Optional<PerfilVendedor> findByUsuarioId(Long usuarioId);
}
