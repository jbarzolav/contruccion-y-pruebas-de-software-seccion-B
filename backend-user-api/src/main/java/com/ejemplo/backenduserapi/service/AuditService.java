package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.entity.RegistroAuditoria;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.RegistroAuditoriaRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra solo eventos tipados y presenta DTOs, nunca entidades de persistencia. */
@Service
public class AuditService {
    private final RegistroAuditoriaRepository registros;

    public AuditService(RegistroAuditoriaRepository registros) {
        this.registros = registros;
    }

    @Transactional
    public void registrar(Usuario usuario, AccionAuditoria accion) {
        if (usuario == null || usuario.getCorreo() == null || accion == null) {
            throw new IllegalArgumentException("La acción de auditoría requiere identidad y tipo válidos.");
        }
        registros.save(new RegistroAuditoria(accion, usuario.getCorreo(), Instant.now()));
    }

}
