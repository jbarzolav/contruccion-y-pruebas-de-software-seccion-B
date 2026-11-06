package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Notificacion;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU 54 - Servicio de lectura: individual, masiva, idempotencia, propiedad de
 * la notificación, fecha de lectura, recálculo de pendientes y transacción.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionLecturaServiceTest {

    @Mock private NotificacionRepository repositorio;
    @InjectMocks private NotificacionService servicio;

    private final Usuario usuario = usuario(10L);
    private final Usuario otro = usuario(20L);

    static Usuario usuario(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    // ---------------------------------------------------------------
    // 1) Lectura individual
    // ---------------------------------------------------------------
    @Test
    void marcaLaNotificacionPropiaComoLeidaGuardandoLaFecha() {
        Notificacion pendiente = pendiente(5L, 10L);
        when(repositorio.findById(5L)).thenReturn(Optional.of(pendiente));
        when(repositorio.save(any(Notificacion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(2L);

        var respuesta = servicio.marcarLeida(usuario, 5L);

        assertEquals(NotificacionService.LEIDA, respuesta.estado());
        assertNotNull(respuesta.fechaLectura());
        assertEquals(2, respuesta.pendientes());
        assertEquals(NotificacionService.LEIDA, pendiente.getEstado());
        assertNotNull(pendiente.getFechaLectura());
        verify(repositorio).save(pendiente);
        verify(repositorio).countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE);
    }

    @Test
    void laFechaDeLecturaEsPosteriorALaFechaDeLaNotificacion() {
        Notificacion pendiente = pendiente(5L, 10L);
        when(repositorio.findById(5L)).thenReturn(Optional.of(pendiente));
        when(repositorio.save(any(Notificacion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repositorio.countByDestinatarioIdAndEstado(anyLong(), anyString())).thenReturn(0L);

        servicio.marcarLeida(usuario, 5L);

        assertTrue(pendiente.getFechaLectura().isAfter(pendiente.getFecha()));
    }

    @Test
    void repetirLaLecturaNoAlteraNiLaFechaNiElEstado() {
        Notificacion yaLeida = pendiente(5L, 10L);
        LocalDateTime primeraLectura = LocalDateTime.of(2026, 11, 1, 9, 30);
        yaLeida.marcarLeida(primeraLectura);
        when(repositorio.findById(5L)).thenReturn(Optional.of(yaLeida));
        when(repositorio.save(any(Notificacion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(0L);

        var respuesta = servicio.marcarLeida(usuario, 5L);

        assertEquals(NotificacionService.LEIDA, respuesta.estado());
        assertEquals(primeraLectura, respuesta.fechaLectura());
        assertEquals(primeraLectura, yaLeida.getFechaLectura());
        assertEquals(0, respuesta.pendientes());
    }

    // ---------------------------------------------------------------
    // 2) Propiedad, identificadores y sesión
    // ---------------------------------------------------------------
    @Test
    void rechazaLaLecturaDeUnaNotificacionDeOtroUsuario() {
        Notificacion ajena = pendiente(5L, 20L);
        when(repositorio.findById(5L)).thenReturn(Optional.of(ajena));

        var excepcion = assertThrows(AutorizacionException.class,
                () -> servicio.marcarLeida(usuario, 5L));

        assertEquals(403, excepcion.getEstado().value());
        assertEquals(NotificacionService.PENDIENTE, ajena.getEstado());
        assertNull(ajena.getFechaLectura());
        verify(repositorio, never()).save(any());
    }

    @Test
    void rechazaNotificacionInexistente() {
        when(repositorio.findById(999L)).thenReturn(Optional.empty());

        var excepcion = assertThrows(AutorizacionException.class,
                () -> servicio.marcarLeida(usuario, 999L));

        assertEquals(404, excepcion.getEstado().value());
        verify(repositorio, never()).save(any());
    }

    @Test
    void rechazaIdentificadoresInvalidos() {
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.marcarLeida(usuario, null)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.marcarLeida(usuario, 0L)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.marcarLeida(usuario, -7L)).getEstado().value());
        verifyNoInteractions(repositorio);
    }

    @Test
    void exigeSesionAutenticada() {
        assertEquals(401, assertThrows(AutorizacionException.class,
                () -> servicio.marcarLeida(null, 5L)).getEstado().value());
        assertEquals(401, assertThrows(AutorizacionException.class,
                () -> servicio.marcarTodasComoLeidas(null)).getEstado().value());
        verifyNoInteractions(repositorio);
    }

    // ---------------------------------------------------------------
    // 3) Lectura masiva
    // ---------------------------------------------------------------
    @Test
    void marcaTodasLasPendientesDelUsuarioAutenticado() {
        when(repositorio.marcarTodasComoLeidas(eq(10L), eq(NotificacionService.LEIDA),
                eq(NotificacionService.PENDIENTE), any(LocalDateTime.class))).thenReturn(3);
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(0L);

        var respuesta = servicio.marcarTodasComoLeidas(usuario);

        assertEquals(3, respuesta.marcadas());
        assertEquals(0, respuesta.pendientes());
        ArgumentCaptor<Long> destinatario = ArgumentCaptor.forClass(Long.class);
        verify(repositorio).marcarTodasComoLeidas(destinatario.capture(),
                eq(NotificacionService.LEIDA), eq(NotificacionService.PENDIENTE),
                any(LocalDateTime.class));
        assertEquals(10L, destinatario.getValue());
    }

    @Test
    void repetirLaLecturaMasivaNoVuelveAMarcarNada() {
        when(repositorio.marcarTodasComoLeidas(anyLong(), anyString(), anyString(),
                any(LocalDateTime.class))).thenReturn(0);
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(0L);

        var respuesta = servicio.marcarTodasComoLeidas(usuario);

        assertEquals(0, respuesta.marcadas());
        assertEquals(0, respuesta.pendientes());
    }

    @Test
    void recalculaLosPendientesTrasLaLecturaMasiva() {
        when(repositorio.marcarTodasComoLeidas(anyLong(), anyString(), anyString(),
                any(LocalDateTime.class))).thenReturn(4);
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(1L);

        var respuesta = servicio.marcarTodasComoLeidas(usuario);

        assertEquals(1, respuesta.pendientes());
        verify(repositorio).countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE);
    }

    @Test
    void laLecturaMasivaEsUnaUnicaOperacionTransaccional() throws Exception {
        var metodo = NotificacionService.class.getMethod("marcarTodasComoLeidas", Usuario.class);
        assertNotNull(metodo.getAnnotation(Transactional.class));
        when(repositorio.marcarTodasComoLeidas(anyLong(), anyString(), anyString(),
                any(LocalDateTime.class))).thenReturn(2);

        servicio.marcarTodasComoLeidas(usuario);

        // Una sola sentencia UPDATE: no hay guardados parciales posibles.
        verify(repositorio, times(1)).marcarTodasComoLeidas(anyLong(), anyString(), anyString(),
                any(LocalDateTime.class));
        verify(repositorio, never()).save(any());
    }

    private Notificacion pendiente(Long id, Long destinatarioId) {
        return new Notificacion(NotificacionService.NUEVO_MENSAJE, "Resumen de prueba",
                LocalDateTime.of(2026, 11, 1, 8, 0), destinatarioId, null, 5L,
                "hu54-test-" + id);
    }
}
