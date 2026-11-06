package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.CalificacionRequest;
import com.ejemplo.backenduserapi.dto.ResenaRequest;
import com.ejemplo.backenduserapi.dto.ResenaEdicionRequest;
import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.CalificacionRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import com.ejemplo.backenduserapi.repository.ResenaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperienciaCompraServiceTest {
    @Mock private PedidoRepository pedidos;
    @Mock private CalificacionRepository calificaciones;
    @Mock private ResenaRepository resenas;
    @InjectMocks private ExperienciaCompraService servicio;
    private Usuario cliente;
    private Pedido pedido;

    @BeforeEach
    void preparar() {
        cliente = new Usuario("cliente@test.com", "hash");
        cliente.setId(10L);
        pedido = new Pedido(10L, LocalDateTime.now());
        pedido.setId(20L);
        pedido.setTotal(BigDecimal.TEN);
        Producto producto = new Producto(30L, "Producto", "Descripción", BigDecimal.TEN, 5,
                "cat", "DISPONIBLE", null, 40L);
        pedido.setDetalles(List.of(new DetallePedido(pedido, producto, 1, BigDecimal.TEN)));
        lenient().when(resenas.findByPedidoIdOrderByFechaCreacionAsc(20L)).thenReturn(List.of());
        lenient().when(calificaciones.findByPedidoId(20L)).thenReturn(Optional.empty());
    }

    @Test
    void calificaCompraYDerivaVendedor() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));
        when(calificaciones.existsByPedidoId(20L)).thenReturn(false);

        servicio.calificar(20L, requestCalificacion(5), cliente);

        var captor = org.mockito.ArgumentCaptor.forClass(com.ejemplo.backenduserapi.entity.Calificacion.class);
        verify(calificaciones).save(captor.capture());
        assertEquals(10L, captor.getValue().getClienteId());
        assertEquals(40L, captor.getValue().getVendedorId());
    }

    @Test
    void rechazaValorFueraDeRango() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));
        assertThrows(ExperienciaCompraException.class, () -> servicio.calificar(20L, requestCalificacion(6), cliente));
        verify(calificaciones, never()).save(any());
    }

    @Test
    void rechazaPedidoAjeno() {
        pedido.setClienteId(99L);
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));
        assertThrows(ExperienciaCompraException.class, () -> servicio.calificar(20L, requestCalificacion(4), cliente));
    }

    @Test
    void rechazaCalificacionDuplicada() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));
        when(calificaciones.existsByPedidoId(20L)).thenReturn(true);
        assertThrows(ExperienciaCompraException.class, () -> servicio.calificar(20L, requestCalificacion(4), cliente));
        verify(calificaciones, never()).save(any());
    }

    @Test
    void publicaResenaNormalizadaYRechazaDestinoInvalido() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));
        when(resenas.existsByPedidoIdAndDestinoTipo(20L, "PRODUCTO")).thenReturn(false);
        servicio.resenar(20L, requestResena(" producto ", "  llegó bien  "), cliente);
        var captor = org.mockito.ArgumentCaptor.forClass(com.ejemplo.backenduserapi.entity.Resena.class);
        verify(resenas).save(captor.capture());
        assertEquals("PRODUCTO", captor.getValue().getDestinoTipo());
        assertEquals("llegó bien", captor.getValue().getTexto());
        assertThrows(ExperienciaCompraException.class,
                () -> servicio.resenar(20L, requestResena("OTRO", "texto"), cliente));
    }

    @Test
    void editaSoloElTextoDeUnaResenaPropia() {
        com.ejemplo.backenduserapi.entity.Resena resena = new com.ejemplo.backenduserapi.entity.Resena(
                pedido, 10L, "PRODUCTO", 30L, null, "texto anterior", LocalDateTime.now());
        when(resenas.findById(50L)).thenReturn(Optional.of(resena));
        ResenaEdicionRequest request = new ResenaEdicionRequest();
        request.setTexto("  texto actualizado  ");

        var respuesta = servicio.editarResena(50L, request, cliente);

        assertEquals("texto actualizado", respuesta.texto());
        assertEquals("PRODUCTO", respuesta.destino());
        assertEquals(30L, respuesta.productoId());
        verify(resenas).save(resena);
    }

    @Test
    void noPermiteEditarResenaAjenaNiTextoVacio() {
        com.ejemplo.backenduserapi.entity.Resena resena = new com.ejemplo.backenduserapi.entity.Resena(
                pedido, 99L, "PRODUCTO", 30L, null, "texto", LocalDateTime.now());
        when(resenas.findById(50L)).thenReturn(Optional.of(resena));
        ResenaEdicionRequest request = new ResenaEdicionRequest();
        request.setTexto("nuevo");
        assertThrows(ExperienciaCompraException.class, () -> servicio.editarResena(50L, request, cliente));

        resena = new com.ejemplo.backenduserapi.entity.Resena(
                pedido, 10L, "PRODUCTO", 30L, null, "texto", LocalDateTime.now());
        when(resenas.findById(51L)).thenReturn(Optional.of(resena));
        request.setTexto("   ");
        assertThrows(ExperienciaCompraException.class, () -> servicio.editarResena(51L, request, cliente));
    }

    @Test
    void listaPedidosUsaLaIdentidadDeSesionYLaConsultaOrdenada() {
        when(pedidos.findByClienteIdOrderByFechaDesc(10L)).thenReturn(List.of(pedido));
        assertEquals(1, servicio.listarPedidos(cliente).size());
        verify(pedidos).findByClienteIdOrderByFechaDesc(10L);
    }

    @Test
    void filtraComprasPorEstadoFechaYMontoConLaSesionDelCliente() {
        when(pedidos.findByClienteIdOrderByFechaDesc(10L)).thenReturn(List.of(pedido));
        var fecha = pedido.getFecha().toLocalDate();

        var resultado = servicio.filtrarPedidos(cliente, " registrado ", fecha, fecha,
                new BigDecimal("10.00"), new BigDecimal("10.00"), 0, 10);

        assertEquals(1, resultado.size());
        assertEquals(20L, resultado.get(0).idPedido());
        verify(pedidos).findByClienteIdOrderByFechaDesc(10L);
    }

    @Test
    void rechazaRangoDeFechasYMontosInvalidosEnHistorial() {
        var fecha = pedido.getFecha().toLocalDate();
        assertThrows(ExperienciaCompraException.class,
                () -> servicio.filtrarPedidos(cliente, null, fecha.plusDays(1), fecha,
                        null, null, 0, 10));
        assertThrows(ExperienciaCompraException.class,
                () -> servicio.filtrarPedidos(cliente, null, null, null,
                        new BigDecimal("20"), new BigDecimal("10"), 0, 10));
    }

    @Test
    void consultaEstadoYFechaDelPedidoPropio() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));

        var estado = servicio.consultarEstado(20L, cliente);

        assertEquals(20L, estado.idPedido());
        assertEquals("REGISTRADO", estado.estado());
        assertEquals(pedido.getFecha(), estado.fechaActualizacion());
    }

    @Test
    void noPermiteConsultarEstadoDePedidoAjeno() {
        pedido.setClienteId(99L);
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));

        assertThrows(ExperienciaCompraException.class, () -> servicio.consultarEstado(20L, cliente));
    }

    /** HU 50 - El detalle usa el precio registrado en la operación y el total persistido. */
    @Test
    void devuelveElDetalleConElPrecioRegistradoYElTotalPersistido() {
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));
        // El catálogo cambió después de la compra: el detalle no debe recalcular.
        pedido.getDetalles().get(0).getProducto().setPrecio(new BigDecimal("999.00"));

        var detalle = servicio.obtenerDetallePedido(20L, cliente);

        assertEquals(20L, detalle.idPedido());
        assertEquals(10L, detalle.clienteId());
        assertEquals("REGISTRADO", detalle.estado());
        assertEquals(1, detalle.detalles().size());
        assertEquals(0, new BigDecimal("10.00").compareTo(detalle.detalles().get(0).getPrecioUnitario()));
        assertEquals(0, new BigDecimal("10.00").compareTo(detalle.detalles().get(0).getSubtotal()));
        assertEquals(0, pedido.getTotal().compareTo(detalle.total()));
    }

    /** HU 50 - Un cliente no puede ver el detalle de la compra de otro. */
    @Test
    void noPermiteVerElDetalleDeUnPedidoAjeno() {
        pedido.setClienteId(99L);
        when(pedidos.findById(20L)).thenReturn(Optional.of(pedido));

        assertThrows(ExperienciaCompraException.class, () -> servicio.obtenerDetallePedido(20L, cliente));
    }

    private CalificacionRequest requestCalificacion(int valor) {
        CalificacionRequest request = new CalificacionRequest(); request.setValor(valor); return request;
    }

    private ResenaRequest requestResena(String destino, String texto) {
        ResenaRequest request = new ResenaRequest(); request.setDestino(destino); request.setTexto(texto); return request;
    }
}
