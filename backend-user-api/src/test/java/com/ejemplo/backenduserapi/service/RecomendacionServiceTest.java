package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ProductoResumenResponse;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU 20 - Prueba unitaria pura (sin levantar Spring) del servicio
 * de recomendaciones por término de búsqueda.
 */
@ExtendWith(MockitoExtension.class)
class RecomendacionServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private RecomendacionService recomendacionService;

    // ============================================================
    // AUXILIARES
    // ============================================================
    private Producto producto(Long id, String nombre, String categoria) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setDescripcion("Producto de prueba HU 20");
        producto.setPrecio(new BigDecimal("50.00"));
        producto.setStock(5);
        producto.setCategoria(categoria);
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(1L);
        return producto;
    }

    // ============================================================
    // TÉRMINO VACÍO O NULO
    // ============================================================
    @Test
    void debeDevolverVacioSinConsultarSiElTerminoEsVacio() {
        List<ProductoResumenResponse> resultado =
                recomendacionService.recomendar("   ");

        assertTrue(resultado.isEmpty());
        verify(productoRepository, never())
                .findByNombreContainingIgnoreCaseAndEstado(anyString(), anyString());
        verify(productoRepository, never())
                .findByCategoriaContainingIgnoreCaseAndEstado(anyString(), anyString());
    }

    @Test
    void debeDevolverVacioSinConsultarSiElTerminoEsNulo() {
        List<ProductoResumenResponse> resultado = recomendacionService.recomendar(null);

        assertTrue(resultado.isEmpty());
        verify(productoRepository, never()).findByNombreContainingIgnoreCaseAndEstado(
                anyString(), anyString());
    }

    // ============================================================
    // COINCIDENCIAS
    // ============================================================
    @Test
    void debeDevolverProductosRelacionadosPorNombre() {
        when(productoRepository.findByNombreContainingIgnoreCaseAndEstado(
                        "arduino", "DISPONIBLE"))
                .thenReturn(List.of(producto(1L, "Arduino Uno", "Microcontroladores")));

        List<ProductoResumenResponse> resultado = recomendacionService.recomendar("arduino");

        assertEquals(1, resultado.size());
        assertEquals(1L, resultado.get(0).getId());
        assertEquals("Arduino Uno", resultado.get(0).getNombre());
        assertEquals("Microcontroladores", resultado.get(0).getCategoria());
        assertEquals(new BigDecimal("50.00"), resultado.get(0).getPrecio());
        assertEquals(5, resultado.get(0).getStock());
        assertEquals("/imagenes/producto.png", resultado.get(0).getImagenUrl());
    }

    @Test
    void debeDevolverVacioSiNoHayCoincidencias() {
        when(productoRepository.findByNombreContainingIgnoreCaseAndEstado(
                        "raspberry", "DISPONIBLE"))
                .thenReturn(List.of());
        when(productoRepository.findByCategoriaContainingIgnoreCaseAndEstado(
                        "raspberry", "DISPONIBLE"))
                .thenReturn(List.of());

        List<ProductoResumenResponse> resultado =
                recomendacionService.recomendar("raspberry");

        assertTrue(resultado.isEmpty());
    }

    // ============================================================
    // DUPLICADOS: mismo producto coincidiendo por nombre y categoría
    // ============================================================
    @Test
    void debeEvitarDuplicadosCuandoCoincideNombreYCategoria() {
        Producto repetido = producto(1L, "Kit Arduino", "Arduino");

        when(productoRepository.findByNombreContainingIgnoreCaseAndEstado(
                        "arduino", "DISPONIBLE"))
                .thenReturn(List.of(repetido));
        when(productoRepository.findByCategoriaContainingIgnoreCaseAndEstado(
                        "arduino", "DISPONIBLE"))
                .thenReturn(List.of(repetido, producto(2L, "Protoboard", "Arduino")));

        List<ProductoResumenResponse> resultado = recomendacionService.recomendar("arduino");

        assertEquals(2, resultado.size());
        assertEquals(1L, resultado.get(0).getId());
        assertEquals(2L, resultado.get(1).getId());
    }

    // ============================================================
    // DISPONIBILIDAD: solo se consultan productos DISPONIBLES
    // (la exclusión real de RETIRADO/INACTIVO/AGOTADO se verifica
    //  en RecomendacionesControllerTest contra H2)
    // ============================================================
    @Test
    void debeConsultarUnicamenteProductosDisponibles() {
        when(productoRepository.findByNombreContainingIgnoreCaseAndEstado(
                        "mouse", "DISPONIBLE"))
                .thenReturn(List.of());
        when(productoRepository.findByCategoriaContainingIgnoreCaseAndEstado(
                        "mouse", "DISPONIBLE"))
                .thenReturn(List.of());

        recomendacionService.recomendar("mouse");

        verify(productoRepository).findByNombreContainingIgnoreCaseAndEstado(
                "mouse", "DISPONIBLE");
        verify(productoRepository).findByCategoriaContainingIgnoreCaseAndEstado(
                "mouse", "DISPONIBLE");
    }
}
