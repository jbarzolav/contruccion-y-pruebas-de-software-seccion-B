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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU 53 - Servicio de la bandeja unificada: filtros combinables, límites de
 * paginación, contador de pendientes, lista vacía, aislamiento y sesión ausente.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionBandejaServiceTest {

    @Mock private NotificacionRepository repositorio;
    @InjectMocks private NotificacionService servicio;

    private final Usuario usuario = usuario(10L);

    static Usuario usuario(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    // ---------------------------------------------------------------
    // 1) Filtros y aislamiento por destinatario
    // ---------------------------------------------------------------
    @Test
    void consultaSoloLasNotificacionesDelUsuarioAutenticado() {
        when(repositorio.buscar(eq(10L), anyList(), anyList(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        servicio.bandeja(usuario, null, null, null, null, 0, 20);

        ArgumentCaptor<Long> destinatario = ArgumentCaptor.forClass(Long.class);
        verify(repositorio).buscar(destinatario.capture(), anyList(), anyList(), isNull(), isNull(),
                any(Pageable.class));
        assertEquals(10L, destinatario.getValue());
    }

    @Test
    void combinaFiltrosDeTipoEstadoYFecha() {
        when(repositorio.buscar(eq(10L), anyList(), anyList(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        servicio.bandeja(usuario, NotificacionService.NUEVO_MENSAJE, NotificacionService.LEIDA,
                "2026-11-01T10:00", "2026-11-05", 0, 20);

        verify(repositorio).buscar(eq(10L), eq(List.of(NotificacionService.NUEVO_MENSAJE)),
                eq(List.of(NotificacionService.LEIDA)),
                eq(LocalDateTime.of(2026, 11, 1, 10, 0)),
                eq(LocalDateTime.of(2026, 11, 5, 23, 59, 59, 999999999)),
                any(Pageable.class));
    }

    @Test
    void sinFiltrosIncluyeTodosLosTiposYEstadosSinRangoDeFecha() {
        when(repositorio.buscar(eq(10L), anyList(), anyList(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        servicio.bandeja(usuario, "", " ", null, null, 0, 20);

        verify(repositorio).buscar(eq(10L),
                eq(List.of(NotificacionService.CAMBIO_ESTADO_PEDIDO,
                        NotificacionService.NUEVA_COMPRA, NotificacionService.NUEVO_MENSAJE)),
                eq(List.of(NotificacionService.PENDIENTE, NotificacionService.LEIDA)),
                isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void rechazaTipoYEstadoNoPermitidos() {
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(usuario, "INVENTADO", null, null, null, 0, 20)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(usuario, null, "LEIDO", null, null, 0, 20)).getEstado().value());
        verifyNoInteractions(repositorio);
    }

    // ---------------------------------------------------------------
    // 2) Paginación y límites
    // ---------------------------------------------------------------
    @Test
    void respetaLaPaginaYTamanioSolicitados() {
        when(repositorio.buscar(eq(10L), anyList(), anyList(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 50), 250));

        var respuesta = servicio.bandeja(usuario, null, null, null, null, 3, 50);

        ArgumentCaptor<Pageable> paginacion = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).buscar(eq(10L), anyList(), anyList(), isNull(), isNull(),
                paginacion.capture());
        assertEquals(3, paginacion.getValue().getPageNumber());
        assertEquals(50, paginacion.getValue().getPageSize());
        assertEquals(250, respuesta.totalElementos());
        assertEquals(5, respuesta.totalPaginas());
        assertTrue(respuesta.tieneSiguiente());
    }

    @Test
    void admiteElTamanioMaximoPermitido() {
        when(repositorio.buscar(eq(10L), anyList(), anyList(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        var respuesta = servicio.bandeja(usuario, null, null, null, null, 0, NotificacionService.TAMANO_MAXIMO);

        assertEquals(NotificacionService.TAMANO_MAXIMO, respuesta.tamano());
    }

    @Test
    void rechazaPaginaYTamanioFueraDeLimites() {
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(usuario, null, null, null, null, -1, 20)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(usuario, null, null, null, null, 0, 0)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(usuario, null, null, null, null, 0, NotificacionService.TAMANO_MAXIMO + 1))
                .getEstado().value());
        verifyNoInteractions(repositorio);
    }

    @Test
    void rechazaFechasMalFormadasYRangoInvertido() {
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(usuario, null, null, "ayer", null, 0, 20)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(usuario, null, null, null, "2026-13-45", 0, 20)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(usuario, null, null, "2026-11-10", "2026-11-01", 0, 20))
                .getEstado().value());
        verifyNoInteractions(repositorio);
    }

    @Test
    void exigeSesionAutenticada() {
        assertEquals(401, assertThrows(AutorizacionException.class,
                () -> servicio.bandeja(null, null, null, null, null, 0, 20)).getEstado().value());
        verifyNoInteractions(repositorio);
    }

    // ---------------------------------------------------------------
    // 3) Contador de pendientes, lista vacía y consulta única
    // ---------------------------------------------------------------
    @Test
    void elContadorDePendientesNoSeLimitaALaPaginaActual() {
        Notificacion unica = notificacion(1L, NotificacionService.NUEVO_MENSAJE, null, 5L);
        when(repositorio.buscar(eq(10L), anyList(), anyList(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unica), PageRequest.of(0, 1), 40));
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(17L);

        var respuesta = servicio.bandeja(usuario, null, null, null, null, 0, 1);

        assertEquals(1, respuesta.notificaciones().size());
        assertEquals(40, respuesta.totalElementos());
        assertEquals(17, respuesta.pendientes());
        verify(repositorio).countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE);
    }

    @Test
    void devuelveListaVaciaCuandoNoHayResultados() {
        when(repositorio.buscar(eq(10L), anyList(), anyList(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(0L);

        var respuesta = servicio.bandeja(usuario, null, null, null, null, 0, 20);

        assertEquals(List.of(), respuesta.notificaciones());
        assertEquals(0, respuesta.totalElementos());
        assertEquals(0, respuesta.totalPaginas());
        assertFalse(respuesta.tieneSiguiente());
        assertEquals(0, respuesta.pendientes());
    }

    @Test
    void ejecutaUnaUnicaConsultaDePaginaYUnaDeConteo() {
        when(repositorio.buscar(eq(10L), anyList(), anyList(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        notificacion(1L, NotificacionService.CAMBIO_ESTADO_PEDIDO, 70L, null),
                        notificacion(2L, NotificacionService.CAMBIO_ESTADO_PEDIDO, 71L, null))));
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(2L);

        var respuesta = servicio.bandeja(usuario, null, null, null, null, 0, 20);

        assertEquals(2, respuesta.notificaciones().size());
        // Sin N+1: una consulta de página y una de conteo por petición.
        verify(repositorio, times(1)).buscar(eq(10L), anyList(), anyList(), isNull(), isNull(),
                any(Pageable.class));
        verify(repositorio, times(1)).countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE);
    }

    @Test
    void cadaAvisoEnlazaASuPedidoOConversacion() {
        when(repositorio.buscar(eq(10L), anyList(), anyList(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        notificacion(1L, NotificacionService.CAMBIO_ESTADO_PEDIDO, 70L, null),
                        notificacion(2L, NotificacionService.NUEVA_COMPRA, 72L, null),
                        notificacion(3L, NotificacionService.NUEVO_MENSAJE, null, 5L))));
        when(repositorio.countByDestinatarioIdAndEstado(10L, NotificacionService.PENDIENTE)).thenReturn(3L);

        var respuesta = servicio.bandeja(usuario, null, null, null, null, 0, 20);

        assertEquals("/pedidos/70", respuesta.notificaciones().get(0).enlace());
        assertEquals("/pedidos-recibidos#pedido-72", respuesta.notificaciones().get(1).enlace());
        assertEquals("/consultas?c=5", respuesta.notificaciones().get(2).enlace());
        assertEquals(70L, respuesta.notificaciones().get(0).idPedido());
        assertEquals(5L, respuesta.notificaciones().get(2).idConversacion());
        assertNotNull(respuesta.notificaciones().get(2).fecha());
    }

    private Notificacion notificacion(Long id, String tipo, Long pedidoId, Long conversacionId) {
        Notificacion notificacion = new Notificacion(tipo, "Resumen " + tipo,
                LocalDateTime.of(2026, 11, 1, 8, 0), 10L, pedidoId, conversacionId,
                "hu53-test-" + id);
        return notificacion;
    }
}
