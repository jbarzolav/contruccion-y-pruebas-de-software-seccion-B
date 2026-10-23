package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.*;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversacionServiceTest {
    ConversacionRepository conversaciones = mock(ConversacionRepository.class);
    MensajeRepository mensajes = mock(MensajeRepository.class);
    ProductoRepository productos = mock(ProductoRepository.class);
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    ConversacionService servicio = new ConversacionService(conversaciones, mensajes, productos, usuarios, 20);
    Usuario cliente = usuario(1L, Rol.CLIENTE), vendedor = usuario(2L, Rol.VENDEDOR);
    Producto producto = new Producto();
    Conversacion conversacion;

    static Usuario usuario(Long id, Rol rol) { Usuario u = new Usuario(); u.setId(id); u.setRol(rol); return u; }
    @BeforeEach void preparar() {
        producto.setId(3L); producto.setNombre("Producto"); producto.setEstado("DISPONIBLE"); producto.setVendedorId(2L);
        conversacion = new Conversacion(producto, cliente, vendedor);
        when(productos.buscarParaConsulta(3L)).thenReturn(Optional.of(producto));
        when(usuarios.findById(2L)).thenReturn(Optional.of(vendedor));
        when(conversaciones.save(any())).thenAnswer(i -> i.getArgument(0));
        when(mensajes.save(any())).thenAnswer(i -> i.getArgument(0));
        when(conversaciones.findById(9L)).thenReturn(Optional.of(conversacion));
    }
    @Test void creaConversacionYMensajeConEmisorDeSesion() {
        servicio.consultar(cliente, 3L, "  Accesorios?  ");
        verify(conversaciones).save(any());
        ArgumentCaptor<Mensaje> captor = ArgumentCaptor.forClass(Mensaje.class);
        verify(mensajes).save(captor.capture());
        assertSame(cliente, captor.getValue().getEmisor());
        assertSame(vendedor, captor.getValue().getConversacion().getVendedor());
        assertEquals("Accesorios?", captor.getValue().getContenido());
        assertNotNull(captor.getValue().getFecha());
    }
    @Test void reutilizaConversacion() {
        when(conversaciones.findByProducto_IdAndCliente_IdAndVendedor_Id(3L, 1L, 2L)).thenReturn(Optional.of(conversacion));
        servicio.consultar(cliente, 3L, "Otra consulta");
        verify(conversaciones, never()).save(any());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   ", "\t\n", "123456789012345678901"})
    void rechazaMensajesInvalidosEnAmbasOperaciones(String texto) {
        assertEquals(400, assertThrows(AutorizacionException.class, () -> servicio.consultar(cliente, 3L, texto)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class, () -> servicio.responder(vendedor, 9L, texto)).getEstado().value());
        verifyNoInteractions(mensajes);
    }
    @Test void productoInexistente() {
        assertEquals(404, assertThrows(AutorizacionException.class, () -> servicio.consultar(cliente, 99L, "Consulta")).getEstado().value());
    }
    @Test void vendedorInvalido() {
        vendedor.setRol(Rol.CLIENTE);
        assertEquals(409, assertThrows(AutorizacionException.class, () -> servicio.consultar(cliente, 3L, "Consulta")).getEstado().value());
    }
    @Test void productoSinVendedor() {
        producto.setVendedorId(null);
        assertEquals(409, assertThrows(AutorizacionException.class, () -> servicio.consultar(cliente, 3L, "Consulta")).getEstado().value());
    }
    @Test void productoRetirado() {
        producto.setEstado("RETIRADO");
        assertEquals(409, assertThrows(AutorizacionException.class, () -> servicio.consultar(cliente, 3L, "Consulta")).getEstado().value());
    }
    @Test void respuestaConEmisorFechaYConversacion() {
        var respuesta = servicio.responder(vendedor, 9L, " Sí incluye ");
        assertEquals(2L, respuesta.emisorId()); assertNotNull(respuesta.fecha());
        assertEquals("Sí incluye", respuesta.contenido());
        ArgumentCaptor<Mensaje> captor = ArgumentCaptor.forClass(Mensaje.class);
        verify(mensajes).save(captor.capture()); assertSame(conversacion, captor.getValue().getConversacion());
    }
    @Test void terceroNoResponde() {
        assertEquals(403, assertThrows(AutorizacionException.class, () -> servicio.responder(usuario(4L, Rol.VENDEDOR), 9L, "Hola")).getEstado().value());
        verifyNoInteractions(mensajes);
    }
    @Test void clienteNoRespondeComoVendedor() {
        assertEquals(403, assertThrows(AutorizacionException.class, () -> servicio.responder(cliente, 9L, "Hola")).getEstado().value());
    }
    @Test void vendedorNoIniciaConsulta() {
        assertEquals(403, assertThrows(AutorizacionException.class, () -> servicio.consultar(vendedor, 3L, "Hola")).getEstado().value());
    }
    @Test void conversacionInexistente() {
        assertEquals(404, assertThrows(AutorizacionException.class, () -> servicio.responder(vendedor, 99L, "Hola")).getEstado().value());
    }
    @Test void historialSeConsultaPaginadoParaUnParticipante() {
        when(conversaciones.findById(9L)).thenReturn(Optional.of(conversacion));
        Mensaje mensaje = new Mensaje(conversacion, vendedor, "Respuesta reciente");
        when(mensajes.findByConversacion_IdOrderByIdDesc(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(mensaje), PageRequest.of(0, 1), 2));

        var pagina = servicio.listarMensajes(cliente, 9L, 0, 1);

        assertEquals(1, pagina.mensajes().size());
        assertEquals("Respuesta reciente", pagina.mensajes().get(0).contenido());
        assertEquals(2, pagina.totalElementos());
        assertTrue(pagina.tieneSiguiente());
        verify(mensajes).findByConversacion_IdOrderByIdDesc(eq(9L), any(Pageable.class));
    }
    @Test void historialRechazaUsuarioAjenoYParametrosInvalidos() {
        when(conversaciones.findById(9L)).thenReturn(Optional.of(conversacion));
        var ajeno = usuario(4L, Rol.CLIENTE);
        assertEquals(403, assertThrows(AutorizacionException.class,
                () -> servicio.listarMensajes(ajeno, 9L, 0, 20)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.listarMensajes(cliente, 9L, -1, 20)).getEstado().value());
        assertEquals(400, assertThrows(AutorizacionException.class,
                () -> servicio.listarMensajes(cliente, 9L, 0, 101)).getEstado().value());
        verify(mensajes, never()).findByConversacion_IdOrderByIdDesc(anyLong(), any(Pageable.class));
    }
}
