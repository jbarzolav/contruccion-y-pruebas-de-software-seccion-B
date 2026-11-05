package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.EstadoPedidoRequest;
import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.HistorialEstadoPedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.HistorialEstadoPedidoRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 49 - Actualización del estado del pedido por el vendedor.
 * transición válida · transición no permitida · estado desconocido ·
 * vendedor ajeno · pedido inexistente · cliente sin permisos.
 */
@ExtendWith(MockitoExtension.class)
class EstadoPedidoServiceTest {

    @Mock private PedidoRepository pedidos;
    @Mock private HistorialEstadoPedidoRepository historial;
    @Mock private NotificacionService notificaciones;
    @InjectMocks private EstadoPedidoService servicio;

    private Usuario vendedor;
    private Pedido pedido;

    @BeforeEach
    void preparar() {
        vendedor = usuario(40L, Rol.VENDEDOR);
        // Fecha del pedido en el pasado: la fecha del cambio siempre es posterior.
        pedido = new Pedido(10L, LocalDateTime.now().minusHours(1));
        pedido.setId(20L);
        pedido.getDetalles().add(new DetallePedido(pedido,
                new Producto(30L, "Laptop", "Descripción", new BigDecimal("800.00"), 5,
                        "Tecnología", "DISPONIBLE", null, 40L),
                1, new BigDecimal("800.00")));
    }

    @Test
    void actualizaEstadoYRegistraElCambioEnElHistorial() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));
        org.mockito.Mockito.when(historial.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(historial.findByPedidoIdOrderByFechaCambioDesc(20L)).thenReturn(List.of());

        var respuesta = servicio.actualizarEstado(20L, new EstadoPedidoRequest("EN_PROCESO"), vendedor);

        assertEquals(20L, respuesta.idPedido());
        assertEquals("REGISTRADO", respuesta.estadoAnterior());
        assertEquals("EN_PROCESO", respuesta.estado());
        assertEquals(List.of("ENTREGADO", "CANCELADO"), respuesta.estadosPermitidos());
        assertEquals("EN_PROCESO", pedido.getEstado());
        assertTrue(pedido.getFechaActualizacionEstado().isAfter(pedido.getFecha()));

        var captor = ArgumentCaptor.forClass(HistorialEstadoPedido.class);
        verify(historial).save(captor.capture());
        assertEquals("REGISTRADO", captor.getValue().getEstadoAnterior());
        assertEquals("EN_PROCESO", captor.getValue().getEstadoNuevo());
        assertEquals(40L, captor.getValue().getUsuarioId());
        verify(notificaciones).notificarCambioEstado(captor.getValue());
    }

    @Test
    void rechazaTransicionNoPermitida() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));

        var excepcion = assertThrows(ExperienciaCompraException.class,
                () -> servicio.actualizarEstado(20L, new EstadoPedidoRequest("ENTREGADO"), vendedor));

        assertEquals(400, excepcion.getEstado().value());
        assertEquals("No se permite pasar de REGISTRADO a ENTREGADO.", excepcion.getMessage());
        assertEquals("REGISTRADO", pedido.getEstado());
        verify(historial, never()).save(any());
        verify(notificaciones, never()).notificarCambioEstado(any());
    }

    @Test
    void rechazaEstadoDesconocido() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));

        var excepcion = assertThrows(ExperienciaCompraException.class,
                () -> servicio.actualizarEstado(20L, new EstadoPedidoRequest("VOLANDO"), vendedor));

        assertEquals(400, excepcion.getEstado().value());
        verify(historial, never()).save(any());
        verify(notificaciones, never()).notificarCambioEstado(any());
    }

    @Test
    void rechazaRepetirElEstadoActual() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));

        var excepcion = assertThrows(ExperienciaCompraException.class,
                () -> servicio.actualizarEstado(20L, new EstadoPedidoRequest("REGISTRADO"), vendedor));

        assertEquals(400, excepcion.getEstado().value());
        verify(historial, never()).save(any());
        verify(notificaciones, never()).notificarCambioEstado(any());
    }

    @Test
    void rechazaVendedorSinProductosEnElPedido() {
        pedido.getDetalles().clear();
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));

        var excepcion = assertThrows(ExperienciaCompraException.class,
                () -> servicio.actualizarEstado(20L, new EstadoPedidoRequest("EN_PROCESO"), vendedor));

        assertEquals(403, excepcion.getEstado().value());
        assertEquals("El pedido no incluye productos de tus publicaciones.", excepcion.getMessage());
        verify(historial, never()).save(any());
        verify(notificaciones, never()).notificarCambioEstado(any());
    }

    @Test
    void rechazaPedidoInexistente() {
        when(pedidos.findById(99L)).thenReturn(Optional.empty());

        var excepcion = assertThrows(ExperienciaCompraException.class,
                () -> servicio.actualizarEstado(99L, new EstadoPedidoRequest("EN_PROCESO"), vendedor));

        assertEquals(404, excepcion.getEstado().value());
        assertEquals("Pedido no encontrado.", excepcion.getMessage());
    }

    @Test
    void rechazaClienteSinPermisoParaActualizar() {
        var excepcion = assertThrows(ExperienciaCompraException.class,
                () -> servicio.actualizarEstado(20L, new EstadoPedidoRequest("EN_PROCESO"), usuario(10L, Rol.CLIENTE)));

        assertEquals(403, excepcion.getEstado().value());
        assertEquals("Solo un vendedor puede actualizar el estado de un pedido.", excepcion.getMessage());
        verify(historial, never()).save(any());
    }

    private Usuario usuario(Long id, Rol rol) {
        Usuario usuario = new Usuario("usuario@test.com", "hash");
        usuario.setId(id);
        usuario.setRol(rol);
        return usuario;
    }
}
