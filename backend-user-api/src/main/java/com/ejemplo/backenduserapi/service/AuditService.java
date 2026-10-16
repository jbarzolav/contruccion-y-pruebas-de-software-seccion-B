package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.PaginaAuditoriaResponse;
import com.ejemplo.backenduserapi.dto.RegistroAuditoriaResponse;
import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.entity.RegistroAuditoria;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.RegistroAuditoriaRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra solo eventos tipados y presenta DTOs, nunca entidades de persistencia. */
@Service
public class AuditService {
    private static final int TAMANO_MAXIMO = 100;
    private final RegistroAuditoriaRepository registros;
    private final AutorizacionService autorizacion;

    public AuditService(RegistroAuditoriaRepository registros, AutorizacionService autorizacion) {
        this.registros = registros;
        this.autorizacion = autorizacion;
    }

    @Transactional
    public void registrar(Usuario usuario, AccionAuditoria accion) {
        if (usuario == null || usuario.getCorreo() == null || accion == null) {
            throw new IllegalArgumentException("La acción de auditoría requiere identidad y tipo válidos.");
        }
        registros.save(new RegistroAuditoria(accion, usuario.getCorreo(), Instant.now()));
    }

    @Transactional(readOnly = true)
    public PaginaAuditoriaResponse consultar(Usuario administrador, int pagina, int tamano) {
        autorizacion.exigirPermiso(administrador, "ASIGNAR_ROLES");
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new AutorizacionException(HttpStatus.BAD_REQUEST,
                    "La página debe ser >= 0 y el tamaño debe estar entre 1 y 100.");
        }
        Sort orden = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));
        Page<RegistroAuditoria> resultados = registros.findAll(PageRequest.of(pagina, tamano, orden));
        return new PaginaAuditoriaResponse(resultados.getContent().stream()
                .map(RegistroAuditoriaResponse::de).toList(), resultados.getNumber(),
                resultados.getSize(), resultados.getTotalElements(), resultados.getTotalPages());
    }
}
