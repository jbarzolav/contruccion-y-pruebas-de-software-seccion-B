package com.ejemplo.backenduserapi.dto;

import com.ejemplo.backenduserapi.entity.RegistroAuditoria;
import java.time.Instant;

public record RegistroAuditoriaResponse(Long id, String accion, String usuario, Instant fecha) {
    public static RegistroAuditoriaResponse de(RegistroAuditoria registro) {
        return new RegistroAuditoriaResponse(registro.getId(), registro.getAccion().name(),
                registro.getUsuario(), registro.getFecha());
    }
}
