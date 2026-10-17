package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.entity.RegistroAuditoria;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.RegistroAuditoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class AuditServiceTest {
    @Mock private RegistroAuditoriaRepository registros;
    private AuditService servicio;

    @BeforeEach
    void configurar() {
        MockitoAnnotations.openMocks(this);
        servicio = new AuditService(registros);
    }

    @Test
    void registraSoloAccionTipadaUsuarioYFechaSinCamposDeCredenciales() {
        Usuario actor = new Usuario("actor@prueba.local", "hash-privado");
        actor.setPasswordHash("hash-privado");
        servicio.registrar(actor, AccionAuditoria.LOGIN);

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(registros).save(captor.capture());
        RegistroAuditoria guardado = captor.getValue();
        assertEquals(AccionAuditoria.LOGIN, guardado.getAccion());
        assertEquals(actor.getCorreo(), guardado.getUsuario());
        assertNotNull(guardado.getFecha());
        assertFalse(guardado.getUsuario().contains("hash"));
    }
}
