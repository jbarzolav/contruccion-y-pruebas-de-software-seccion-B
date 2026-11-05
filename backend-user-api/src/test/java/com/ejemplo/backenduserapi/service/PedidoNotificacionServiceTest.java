package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Carrito;
import com.ejemplo.backenduserapi.entity.ItemCarrito;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.CarritoRepository;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoNotificacionServiceTest {

    @Mock private PedidoRepository pedidos;
    @Mock private CarritoRepository carritos;
    @Mock private ItemCarritoRepository items;
    @Mock private ProductoRepository productos;
    @Mock private NotificacionService notificaciones;
    @InjectMocks private PedidoService servicio;

    @Test
    void notificaVendedoresSoloDespuesDeRegistrarLaCompra() {
        Carrito carrito = new Carrito(10L);
        carrito.setId(5L);
        Producto producto = new Producto(1L, "Celular", "Descripción", new BigDecimal("100.00"),
                5, "Tecnología", "DISPONIBLE", null, 30L);
        ItemCarrito item = new ItemCarrito(carrito, producto, 1, new BigDecimal("100.00"));
        when(carritos.findByClienteId(10L)).thenReturn(Optional.of(carrito));
        when(items.findByCarritoId(5L)).thenReturn(List.of(item));
        when(pedidos.save(any(Pedido.class))).thenAnswer(invocacion -> {
            Pedido guardado = invocacion.getArgument(0);
            guardado.setId(90L);
            return guardado;
        });

        servicio.confirmarCompra(10L);

        verify(notificaciones).notificarNuevaCompra(org.mockito.ArgumentMatchers.argThat(pedido ->
                pedido.getId().equals(90L) && pedido.getDetalles().size() == 1));
    }

    @Test
    void noNotificaCuandoElCarritoNoTieneItems() {
        Carrito carrito = new Carrito(10L);
        carrito.setId(5L);
        when(carritos.findByClienteId(10L)).thenReturn(Optional.of(carrito));
        when(items.findByCarritoId(5L)).thenReturn(List.of());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.ejemplo.backenduserapi.exception.PedidoInvalidoException.class,
                () -> servicio.confirmarCompra(10L));

        verify(notificaciones, never()).notificarNuevaCompra(any());
    }
}
