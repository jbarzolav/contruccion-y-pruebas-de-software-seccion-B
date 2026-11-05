package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByDestinatarioIdOrderByFechaDesc(Long destinatarioId);
    boolean existsByClaveEvento(String claveEvento);
}
