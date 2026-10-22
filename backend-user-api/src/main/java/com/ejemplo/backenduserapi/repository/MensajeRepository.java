package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByConversacion_IdOrderByIdAsc(Long conversacion);
}
