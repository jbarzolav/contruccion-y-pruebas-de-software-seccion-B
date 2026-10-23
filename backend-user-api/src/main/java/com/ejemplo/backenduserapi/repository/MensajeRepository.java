package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Mensaje;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByConversacion_IdOrderByIdAsc(Long conversacion);
    Page<Mensaje> findByConversacion_IdOrderByIdDesc(Long conversacion, Pageable pageable);
    java.util.Optional<Mensaje> findFirstByConversacion_IdOrderByIdDesc(Long conversacion);
}
