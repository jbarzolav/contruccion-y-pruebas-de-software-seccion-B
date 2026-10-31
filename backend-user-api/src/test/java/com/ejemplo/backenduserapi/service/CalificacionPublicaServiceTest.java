package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.CalificacionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalificacionPublicaServiceTest {

    @Mock private CalificacionRepository calificaciones;
    @Mock private UsuarioRepository usuarios;
    private CalificacionPublicaService servicio;
    private Usuario vendedor;

    @BeforeEach
    void preparar() {
        servicio = new CalificacionPublicaService(calificaciones, usuarios);
        vendedor = new Usuario("vendedor@pulgatec.test", "hash");
        vendedor.setId(40L);
        vendedor.setRol(Rol.VENDEDOR);
    }

    @Test
    void calculaPromedioCantidadYDistribucionSoloDelVendedorConsultado() {
        when(usuarios.findById(40L)).thenReturn(Optional.of(vendedor));
        when(calificaciones.countByVendedorIdAndValorBetween(40L, 1, 5)).thenReturn(3L);
        when(calificaciones.calcularPromedioPorVendedor(40L)).thenReturn(4.3333);
        when(calificaciones.contarPorValorYVendedor(40L)).thenReturn(Arrays.asList(
                new Object[]{3, 1L}, new Object[]{5, 2L}));

        var respuesta = servicio.obtenerResumen(40L);

        assertEquals(40L, respuesta.vendedorId());
        assertEquals("4.33", respuesta.promedio().toPlainString());
        assertEquals(3L, respuesta.cantidad());
        assertEquals(1L, respuesta.distribucion().get(3));
        assertEquals(2L, respuesta.distribucion().get(5));
        assertEquals(0L, respuesta.distribucion().get(1));
        assertEquals(0L, respuesta.distribucion().get(2));
        assertEquals(0L, respuesta.distribucion().get(4));
        verify(calificaciones).countByVendedorIdAndValorBetween(40L, 1, 5);
        verify(calificaciones).calcularPromedioPorVendedor(40L);
        verify(calificaciones).contarPorValorYVendedor(40L);
    }

    @Test
    void vendedorSinCalificacionesRecibeResumenEnCeros() {
        when(usuarios.findById(40L)).thenReturn(Optional.of(vendedor));
        when(calificaciones.countByVendedorIdAndValorBetween(40L, 1, 5)).thenReturn(0L);
        when(calificaciones.calcularPromedioPorVendedor(40L)).thenReturn(null);
        when(calificaciones.contarPorValorYVendedor(40L)).thenReturn(List.of());

        var respuesta = servicio.obtenerResumen(40L);

        assertEquals("0.00", respuesta.promedio().toPlainString());
        assertEquals(0L, respuesta.cantidad());
        assertEquals(5, respuesta.distribucion().size());
        assertEquals(List.of(1, 2, 3, 4, 5), List.copyOf(respuesta.distribucion().keySet()));
        assertEquals(List.of(0L, 0L, 0L, 0L, 0L), List.copyOf(respuesta.distribucion().values()));
    }

    @Test
    void identificadorInvalidoSeRechazaAntesDeConsultarRepositorios() {
        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.obtenerResumen(0L));

        assertEquals(HttpStatus.BAD_REQUEST, error.getEstado());
        verifyNoInteractions(calificaciones, usuarios);
    }

    @Test
    void vendedorInexistenteDevuelveNoEncontrado() {
        when(usuarios.findById(999L)).thenReturn(Optional.empty());

        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.obtenerResumen(999L));

        assertEquals(HttpStatus.NOT_FOUND, error.getEstado());
        verifyNoInteractions(calificaciones);
    }

    @Test
    void cuentaQueNoEsVendedorNoSeExponeComoVendedor() {
        Usuario cliente = new Usuario("cliente@pulgatec.test", "hash");
        cliente.setId(41L);
        cliente.setRol(Rol.CLIENTE);
        when(usuarios.findById(41L)).thenReturn(Optional.of(cliente));

        ExperienciaCompraException error = assertThrows(ExperienciaCompraException.class,
                () -> servicio.obtenerResumen(41L));

        assertEquals(HttpStatus.NOT_FOUND, error.getEstado());
        verify(calificaciones, org.mockito.Mockito.never()).countByVendedorIdAndValorBetween(41L, 1, 5);
    }
}
