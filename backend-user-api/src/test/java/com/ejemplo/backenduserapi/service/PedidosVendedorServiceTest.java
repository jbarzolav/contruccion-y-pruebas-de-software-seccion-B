package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidosVendedorServiceTest {
    @Mock private PedidoRepository pedidos;
    @InjectMocks private PedidosVendedorService servicio;

    @Test
    void devuelveSoloLasLineasYElTotalDelVendedorAutenticado() {
        Usuario vendedor = usuario(40L, Rol.VENDEDOR);
        Pedido pedido = new Pedido(10L, LocalDateTime.now());
        pedido.setId(20L);
        pedido.getDetalles().add(new DetallePedido(pedido, producto(1L, 40L, "Laptop"), 1, new BigDecimal("800.00")));
        pedido.getDetalles().add(new DetallePedido(pedido, producto(2L, 41L, "Audífonos"), 2, new BigDecimal("50.00")));
        when(pedidos.findPedidosConProductosDeVendedor(40L)).thenReturn(List.of(pedido));

        var respuesta = servicio.listarPedidos(vendedor);

        assertEquals(1, respuesta.size());
        assertEquals(new BigDecimal("800.00"), respuesta.get(0).total());
        assertEquals(1, respuesta.get(0).productos().size());
        assertEquals("Laptop", respuesta.get(0).productos().get(0).getNombreProducto());
        verify(pedidos).findPedidosConProductosDeVendedor(40L);
    }

    @Test
    void vendedorSinPedidosRecibeListaVacia() {
        Usuario vendedor = usuario(40L, Rol.VENDEDOR);
        when(pedidos.findPedidosConProductosDeVendedor(40L)).thenReturn(List.of());

        assertEquals(List.of(), servicio.listarPedidos(vendedor));
    }

    @Test
    void rechazaCuentaQueNoTieneRolVendedor() {
        Usuario cliente = usuario(10L, Rol.CLIENTE);

        assertThrows(ExperienciaCompraException.class, () -> servicio.listarPedidos(cliente));
    }

    private Usuario usuario(Long id, Rol rol) {
        Usuario usuario = new Usuario("usuario@test.com", "hash");
        usuario.setId(id);
        usuario.setRol(rol);
        return usuario;
    }

    private Producto producto(Long id, Long vendedorId, String nombre) {
        return new Producto(id, nombre, "Descripción", BigDecimal.TEN, 5,
                "Tecnología", "DISPONIBLE", null, vendedorId);
    }
}
