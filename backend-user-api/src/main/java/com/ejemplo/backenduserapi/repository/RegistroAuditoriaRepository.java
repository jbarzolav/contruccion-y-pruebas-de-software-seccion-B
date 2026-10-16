package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

/** Adaptador de persistencia del historial; el servicio no depende del motor de BD. */
public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> { }
