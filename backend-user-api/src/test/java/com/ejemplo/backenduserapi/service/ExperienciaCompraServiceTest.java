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

    private CalificacionRequest requestCalificacion(int valor) {
        CalificacionRequest request = new CalificacionRequest(); request.setValor(valor); return request;
    }

    private ResenaRequest requestResena(String destino, String texto) {
        ResenaRequest request = new ResenaRequest(); request.setDestino(destino); request.setTexto(texto); return request;
    }
}
