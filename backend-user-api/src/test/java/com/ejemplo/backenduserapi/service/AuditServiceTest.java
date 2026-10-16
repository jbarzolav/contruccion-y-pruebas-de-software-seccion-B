package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.entity.RegistroAuditoria;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.RegistroAuditoriaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTest {
    @Mock private RegistroAuditoriaRepository registros;
    @Mock private AutorizacionService autorizacion;
    private AuditService servicio;
    private Usuario admin;

    @BeforeEach
    void configurar() {
        org.mockito.MockitoAnnotations.openMocks(this);
        servicio = new AuditService(registros, autorizacion);
        admin = new Usuario("admin@prueba.local", "hash-no-se-registra");
        admin.setRol(Rol.ADMINISTRADOR);
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

    @Test
    void consultaPaginadaDevuelveCamposPublicosYOrdenQueSolicitoElService() {
        RegistroAuditoria nuevo = new RegistroAuditoria(AccionAuditoria.LOGOUT,
                "usuario@prueba.local", Instant.parse("2026-10-16T12:00:00Z"));
        RegistroAuditoria anterior = new RegistroAuditoria(AccionAuditoria.LOGIN,
                "usuario@prueba.local", Instant.parse("2026-10-15T12:00:00Z"));
        when(registros.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(nuevo), PageRequest.of(0, 1), 2));

        var pagina = servicio.consultar(admin, 0, 1);

        assertEquals(2, pagina.totalElementos());
        assertEquals(2, pagina.totalPaginas());
        assertEquals(1, pagina.contenido().size());
        assertEquals("LOGOUT", pagina.contenido().getFirst().accion());
        assertEquals("usuario@prueba.local", pagina.contenido().getFirst().usuario());
        assertEquals(nuevo.getFecha(), pagina.contenido().getFirst().fecha());
        assertFalse(pagina.toString().contains("password"));
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(registros).findAll(captor.capture());
        assertEquals("fecha: DESC,id: DESC", captor.getValue().getSort().toString());
    }

    @Test
    void filtrosInvalidosDevuelven400SinConsultarRepositorio() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(AutorizacionException.class,
                () -> servicio.consultar(admin, -1, 20)).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(AutorizacionException.class,
                () -> servicio.consultar(admin, 0, 101)).getEstado());
        verifyNoInteractions(registros);
    }

    @Test
    void usuarioSinPermisoNoConsultaHistorial() {
        Usuario cliente = new Usuario("cliente@prueba.local", "hash");
        cliente.setRol(Rol.CLIENTE);
        doThrow(new AutorizacionException(HttpStatus.FORBIDDEN, "No autorizado."))
                .when(autorizacion).exigirPermiso(cliente, "ASIGNAR_ROLES");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(AutorizacionException.class,
                () -> servicio.consultar(cliente, 0, 20)).getEstado());
        verifyNoInteractions(registros);
    }
}
