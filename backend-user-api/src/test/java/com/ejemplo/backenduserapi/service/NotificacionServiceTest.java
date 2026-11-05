package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.HistorialEstadoPedido;
import com.ejemplo.backenduserapi.entity.Notificacion;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock private NotificacionRepository repositorio;
    @InjectMocks private NotificacionService servicio;

    @Test
    void generaAvisoParaElClienteTrasCambioDeEstado() {
        Pedido pedido = pedido(90L, 10L);
        HistorialEstadoPedido cambio = new HistorialEstadoPedido(
                pedido, "REGISTRADO", "EN_PROCESO", LocalDateTime.of(2026, 11, 4, 10, 0), 30L);
        cambio.setId(42L);
        when(repositorio.existsByClaveEvento("estado:42")).thenReturn(false);

        servicio.notificarCambioEstado(cambio);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(repositorio).save(captor.capture());
        assertEquals(NotificacionService.CAMBIO_ESTADO_PEDIDO, captor.getValue().getTipo());
        assertEquals(10L, captor.getValue().getDestinatarioId());
        assertEquals(90L, captor.getValue().getPedidoId());
        assertEquals("PENDIENTE", captor.getValue().getEstado());
    }

    @Test
    void noDuplicaAvisoDelMismoCambioDeEstado() {
        Pedido pedido = pedido(90L, 10L);
        HistorialEstadoPedido cambio = new HistorialEstadoPedido(
                pedido, "REGISTRADO", "EN_PROCESO", LocalDateTime.now(), 30L);
        cambio.setId(42L);
        when(repositorio.existsByClaveEvento("estado:42")).thenReturn(true);

        servicio.notificarCambioEstado(cambio);

        verify(repositorio, never()).save(any());
    }

    @Test
    void listaSoloLasNotificacionesDelUsuarioSolicitante() {
        Usuario cliente = new Usuario("cliente@test.com", "hash");
        cliente.setId(10L);
        when(repositorio.findByDestinatarioIdOrderByFechaDesc(10L)).thenReturn(List.of());

        assertEquals(List.of(), servicio.listarPropias(cliente));
        verify(repositorio).findByDestinatarioIdOrderByFechaDesc(10L);
    }

    @Test
    void generaUnAvisoPorCadaVendedorDistintoEnLaCompra() {
        Pedido pedido = pedido(90L, 10L);
        pedido.getDetalles().add(detalle(pedido, 30L));
        pedido.getDetalles().add(detalle(pedido, 30L));
        pedido.getDetalles().add(detalle(pedido, 40L));
        when(repositorio.existsByClaveEvento(any())).thenReturn(false);

        servicio.notificarNuevaCompra(pedido);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(repositorio, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(List.of(30L, 40L), captor.getAllValues().stream()
                .map(Notificacion::getDestinatarioId).toList());
        assertEquals(List.of("compra:90:30", "compra:90:40"), captor.getAllValues().stream()
                .map(Notificacion::getClaveEvento).toList());
    }

    @Test
    void omiteAvisosDeCompraYaRegistrados() {
        Pedido pedido = pedido(90L, 10L);
        pedido.getDetalles().add(detalle(pedido, 30L));
        when(repositorio.existsByClaveEvento("compra:90:30")).thenReturn(true);

        servicio.notificarNuevaCompra(pedido);

        verify(repositorio, never()).save(any());
    }

    private Pedido pedido(Long id, Long clienteId) {
        Pedido pedido = new Pedido(clienteId, LocalDateTime.now());
        pedido.setId(id);
        return pedido;
    }

    private DetallePedido detalle(Pedido pedido, Long vendedorId) {
        Producto producto = new Producto(1L, "Accesorio", "Descripción", BigDecimal.TEN,
                5, "Tecnología", "DISPONIBLE", null, vendedorId);
        return new DetallePedido(pedido, producto, 1, BigDecimal.TEN);
    }
}
