package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.UmbralStock;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.exception.PropietarioInvalidoException;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.ejemplo.backenduserapi.repository.UmbralStockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 58 - Umbral de stock y deteccion del cruce.
 *
 * Cubre la maquina de estados del aviso: 8 -> 5 avisa, 5 -> 4 y 4 -> 3 no
 * repiten aviso, 3 -> 8 rearma y 8 -> 5 vuelve a avisar.
 */
@ExtendWith(MockitoExtension.class)
class AlertaStockServiceTest {

    @Mock
    private UmbralStockRepository umbrales;
    @Mock
    private ProductoRepository productos;
    @Mock
    private NotificacionService notificaciones;
    @InjectMocks
    private AlertaStockService servicio;

    private static final long VENDEDOR = 40L;
    private static final long OTRO_VENDEDOR = 41L;

    @Test
    void avisaCuandoElStockCruzaElUmbralExactamente() {
        Producto producto = producto(1L, VENDEDOR, 8);
        UmbralStock umbral = umbral(producto, 5);
        when(umbrales.buscarParaActualizar(1L)).thenReturn(Optional.of(umbral));

        servicio.registrarCambioStock(producto, 8, 5);

        assertTrue(umbral.isAlertaDisparada());
        assertEquals(1, umbral.getAlertasEmitidas());
        verify(notificaciones).notificarStockBajo(producto, 5, 5, 1);
    }

    @Test
    void noRepiteElAvisoCuandoElStockSigueBajando() {
        Producto producto = producto(1L, VENDEDOR, 5);
        UmbralStock umbral = umbral(producto, 5);
        umbral.setAlertaDisparada(true);
        umbral.setAlertasEmitidas(1);
        when(umbrales.buscarParaActualizar(1L)).thenReturn(Optional.of(umbral));

        servicio.registrarCambioStock(producto, 5, 4);
        servicio.registrarCambioStock(producto, 4, 3);

        assertEquals(1, umbral.getAlertasEmitidas());
        verify(notificaciones, never()).notificarStockBajo(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void rearmaLaAlertaTrasUnaReposicion() {
        Producto producto = producto(1L, VENDEDOR, 3);
        UmbralStock umbral = umbral(producto, 5);
        umbral.setAlertaDisparada(true);
        when(umbrales.buscarParaActualizar(1L)).thenReturn(Optional.of(umbral));

        servicio.registrarCambioStock(producto, 3, 8);

        assertFalse(umbral.isAlertaDisparada());
        verify(notificaciones, never()).notificarStockBajo(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void vuelveAAvisarEnElSiguienteCruceDespuesDeLaReposicion() {
        Producto producto = producto(1L, VENDEDOR, 8);
        UmbralStock umbral = umbral(producto, 5);
        when(umbrales.buscarParaActualizar(1L)).thenReturn(Optional.of(umbral));

        servicio.registrarCambioStock(producto, 8, 5);
        servicio.registrarCambioStock(producto, 5, 4);
        servicio.registrarCambioStock(producto, 4, 3);
        servicio.registrarCambioStock(producto, 3, 8);
        servicio.registrarCambioStock(producto, 8, 5);

        assertEquals(2, umbral.getAlertasEmitidas());
        verify(notificaciones).notificarStockBajo(producto, 5, 5, 1);
        verify(notificaciones).notificarStockBajo(producto, 5, 5, 2);
    }

    @Test
    void noAplicaNingunUmbralCuandoElProductoNoTieneConfigurado() {
        Producto producto = producto(1L, VENDEDOR, 8);
        when(umbrales.buscarParaActualizar(1L)).thenReturn(Optional.empty());

        servicio.registrarCambioStock(producto, 8, 5);

        verify(notificaciones, never()).notificarStockBajo(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void configuraElUmbralDeUnaPublicacionPropia() {
        Usuario vendedor = vendedor(VENDEDOR);
        Producto producto = producto(1L, VENDEDOR, 8);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(umbrales.buscarParaActualizar(1L)).thenReturn(Optional.empty());
        when(umbrales.save(any(UmbralStock.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = servicio.configurar(vendedor, 1L, 5);

        assertEquals(5, respuesta.umbral());
        assertEquals(8, respuesta.stock());
        assertFalse(respuesta.stockBajo());
        assertFalse(respuesta.alertaNotificada());
    }

    @Test
    void alConfigurarConElStockYaBajoNoEmiteAvisoPeroMarcaElEstadoBajo() {
        Usuario vendedor = vendedor(VENDEDOR);
        Producto producto = producto(1L, VENDEDOR, 3);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(umbrales.buscarParaActualizar(1L)).thenReturn(Optional.empty());
        when(umbrales.save(any(UmbralStock.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = servicio.configurar(vendedor, 1L, 5);

        assertTrue(respuesta.stockBajo());
        // El episodio actual queda armado como ya notificado: no habra aviso
        // hasta que haya una reposicion y un nuevo cruce.
        assertTrue(respuesta.alertaNotificada());
        verify(notificaciones, never()).notificarStockBajo(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void aceptaElUmbralCero() {
        Usuario vendedor = vendedor(VENDEDOR);
        Producto producto = producto(1L, VENDEDOR, 4);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(umbrales.buscarParaActualizar(1L)).thenReturn(Optional.empty());
        when(umbrales.save(any(UmbralStock.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = servicio.configurar(vendedor, 1L, 0);

        assertEquals(0, respuesta.umbral());
        assertFalse(respuesta.stockBajo());
    }

    @Test
    void rechazaElUmbralNegativo() {
        Usuario vendedor = vendedor(VENDEDOR);

        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.configurar(vendedor, 1L, -1));

        assertEquals(400, error.getEstado().value());
    }

    @Test
    void rechazaModificarElUmbralDeUnaPublicacionAjena() {
        Usuario vendedor = vendedor(VENDEDOR);
        Producto producto = producto(1L, OTRO_VENDEDOR, 8);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));

        assertThrows(PropietarioInvalidoException.class,
                () -> servicio.configurar(vendedor, 1L, 5));
    }

    @Test
    void sinUmbralConfiguradoLaConsultaDevuelveElUmbralVacio() {
        Usuario vendedor = vendedor(VENDEDOR);
        Producto producto = producto(1L, VENDEDOR, 8);
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(umbrales.findByProductoId(1L)).thenReturn(Optional.empty());

        var respuesta = servicio.consultar(vendedor, 1L);

        assertNull(respuesta.umbral());
        assertFalse(respuesta.stockBajo());
    }

    @Test
    void rechazaACuentaQueNoEsVendedor() {
        Usuario cliente = new Usuario("cliente@test.com", "hash");
        cliente.setId(10L);
        cliente.setRol(Rol.CLIENTE);

        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.consultar(cliente, 1L));

        assertEquals(403, error.getEstado().value());
    }

    private Usuario vendedor(long id) {
        Usuario usuario = new Usuario("vendedor@test.com", "hash");
        usuario.setId(id);
        usuario.setRol(Rol.VENDEDOR);
        return usuario;
    }

    private Producto producto(Long id, Long vendedorId, int stock) {
        Producto producto = new Producto(id, "Mouse", "Descripcion", BigDecimal.TEN, stock,
                "Accesorios", "DISPONIBLE", "/imagenes/mouse.png", vendedorId);
        return producto;
    }

    private UmbralStock umbral(Producto producto, int valor) {
        return new UmbralStock(producto, valor, LocalDateTime.now());
    }
}
