package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Calificacion;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Resena;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.repository.CalificacionRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.ejemplo.backenduserapi.repository.ResenaRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResenaProductoPublicaServiceTest {

    @Mock private ProductoRepository productos;
    @Mock private ResenaRepository resenas;
    @Mock private UsuarioRepository usuarios;
    @Mock private CalificacionRepository calificaciones;
    private ResenaProductoPublicaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ResenaProductoPublicaService(productos, resenas, usuarios, calificaciones);
    }

    @Test
    void listaResenasDelProductoConAutorVisibleYCalificacionDeCompra() {
        Pedido pedido = new Pedido(11L, LocalDateTime.now());
        pedido.setId(70L);
        Resena resena = new Resena(pedido, 11L, "PRODUCTO", 90L, null,
                "Llegó en buen estado", LocalDateTime.of(2026, 10, 30, 12, 0));
        Usuario autor = new Usuario("ana@privado.test", "hash");
        autor.setId(11L);
        autor.setNombre("Ana Torres");
        Calificacion calificacion = new Calificacion(pedido, 11L, 20L, 5, LocalDateTime.now());
        when(productos.existsById(90L)).thenReturn(true);
        when(resenas.findByProductoIdAndDestinoTipoOrderByFechaCreacionDescIdDesc(90L, "PRODUCTO"))
                .thenReturn(List.of(resena));
        when(usuarios.findById(11L)).thenReturn(Optional.of(autor));
        when(calificaciones.findByPedidoId(70L)).thenReturn(Optional.of(calificacion));

        var respuesta = servicio.listarPorProducto(90L);

        assertEquals(1, respuesta.size());
        assertEquals(90L, respuesta.getFirst().productoId());
        assertEquals("Ana Torres", respuesta.getFirst().nombreAutor());
        assertEquals(5, respuesta.getFirst().calificacion());
        assertEquals("Llegó en buen estado", respuesta.getFirst().texto());
        assertEquals(LocalDateTime.of(2026, 10, 30, 12, 0), respuesta.getFirst().fecha());
        verify(resenas).findByProductoIdAndDestinoTipoOrderByFechaCreacionDescIdDesc(90L, "PRODUCTO");
    }

    @Test
    void resenaSinNombreNiCalificacionNoExponeCorreoNiInventaPuntaje() {
        Pedido pedido = new Pedido(11L, LocalDateTime.now());
        pedido.setId(71L);
        Resena resena = new Resena(pedido, 11L, "PRODUCTO", 90L, null,
                "Buena compra", LocalDateTime.now());
        Usuario autor = new Usuario("correo-privado@pulgatec.test", "hash");
        autor.setId(11L);
        when(productos.existsById(90L)).thenReturn(true);
        when(resenas.findByProductoIdAndDestinoTipoOrderByFechaCreacionDescIdDesc(90L, "PRODUCTO"))
                .thenReturn(List.of(resena));
        when(usuarios.findById(11L)).thenReturn(Optional.of(autor));
        when(calificaciones.findByPedidoId(71L)).thenReturn(Optional.empty());

        var respuesta = servicio.listarPorProducto(90L);

        assertEquals("Usuario", respuesta.getFirst().nombreAutor());
        assertNull(respuesta.getFirst().calificacion());
    }

    @Test
    void productoSinResenasDevuelveListaVacia() {
        when(productos.existsById(90L)).thenReturn(true);
        when(resenas.findByProductoIdAndDestinoTipoOrderByFechaCreacionDescIdDesc(90L, "PRODUCTO"))
                .thenReturn(List.of());

        assertEquals(List.of(), servicio.listarPorProducto(90L));
        verifyNoInteractions(usuarios, calificaciones);
    }

    @Test
    void productoInexistenteDevuelveNoEncontradoSinConsultarResenas() {
        when(productos.existsById(999L)).thenReturn(false);

        assertThrows(ProductoNoEncontradoException.class, () -> servicio.listarPorProducto(999L));
        verifyNoInteractions(resenas, usuarios, calificaciones);
    }

    @Test
    void identificadorInvalidoDevuelveErrorDeSolicitud() {
        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.listarPorProducto(0L));

        assertEquals(HttpStatus.BAD_REQUEST, error.getEstado());
        verifyNoInteractions(productos, resenas, usuarios, calificaciones);
    }
}
