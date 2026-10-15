package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** HU 21 - busca la cuenta por correo sin distinguir mayúsculas. */
    Optional<Usuario> findByCorreoIgnoreCase(String correo);
}
