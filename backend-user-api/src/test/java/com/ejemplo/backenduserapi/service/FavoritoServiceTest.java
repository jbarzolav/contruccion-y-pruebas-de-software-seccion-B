package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Favorito;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.exception.FavoritoNoEncontradoException;
import com.ejemplo.backenduserapi.repository.FavoritoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU 31 - Reglas unitarias del servicio de favoritos y su repositorio. */
@ExtendWith(MockitoExtension.class)
class FavoritoServiceTest {
    @Mock private FavoritoRepository favoritos;
    @Mock private ProductoRepository productos;
    @InjectMocks private FavoritoService servicio;

    private Usuario cliente;
    private Producto producto;

    @BeforeEach
    void prepararDatos() {
        cliente = new Usuario("cliente@prueba.com", "hash");
        cliente.setId(3L);
        producto = new Producto();
        producto.setId(7L);
        producto.setNombre("Producto de prueba");
    }

    @Test
    void guardaUnProductoValido() {
        when(productos.findById(7L)).thenReturn(Optional.of(producto));
        when(favoritos.existsByUsuario_IdAndProducto_Id(3L, 7L)).thenReturn(false);

        var respuesta = servicio.agregar(cliente, 7L);

        assertEquals(7L, respuesta.productoId());
        assertTrue(respuesta.favorito());
        verify(favoritos).save(any(Favorito.class));
    }

    @Test
    void productoRepetidoNoSeGuardaNuevamente() {
        when(productos.findById(7L)).thenReturn(Optional.of(producto));
        when(favoritos.existsByUsuario_IdAndProducto_Id(3L, 7L)).thenReturn(true);

        var respuesta = servicio.agregar(cliente, 7L);

        assertTrue(respuesta.favorito());
        verify(favoritos, never()).save(any(Favorito.class));
    }

    @Test
    void productoInexistenteGeneraElErrorDeDominio() {
        when(productos.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ProductoNoEncontradoException.class, () -> servicio.agregar(cliente, 999L));
        verify(favoritos, never()).save(any(Favorito.class));
    }

    @Test
    void listaSoloLosProductosFavoritosDelCliente() {
        when(favoritos.findByUsuario_IdOrderByIdDesc(3L))
                .thenReturn(List.of(new Favorito(cliente, producto)));

        assertEquals(List.of(producto), servicio.listar(cliente));
    }

    @Test
    void eliminaSoloLaRelacionDelClienteYConservaElProducto() {
        Favorito favorito = new Favorito(cliente, producto);
        when(favoritos.findByUsuario_IdAndProducto_Id(3L, 7L)).thenReturn(Optional.of(favorito));
        Integer stockAntes = producto.getStock();

        servicio.eliminar(cliente, 7L);

        verify(favoritos).delete(favorito);
        assertEquals(stockAntes, producto.getStock());
        assertEquals("Producto de prueba", producto.getNombre());
    }

    @Test
    void eliminarFavoritoDeOtraCuentaNoPermiteManipularlo() {
        when(favoritos.findByUsuario_IdAndProducto_Id(3L, 7L)).thenReturn(Optional.empty());

        assertThrows(FavoritoNoEncontradoException.class, () -> servicio.eliminar(cliente, 7L));
        verify(favoritos, never()).delete(any(Favorito.class));
    }

    @Test
    void eliminarFavoritoRepetidoEsUn404Controlado() {
        when(favoritos.findByUsuario_IdAndProducto_Id(3L, 7L)).thenReturn(Optional.empty());

        assertThrows(FavoritoNoEncontradoException.class, () -> servicio.eliminar(cliente, 7L));
        verify(favoritos, never()).delete(any(Favorito.class));
    }
}
