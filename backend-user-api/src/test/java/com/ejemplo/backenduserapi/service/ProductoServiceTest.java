package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ProductoRequest;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria pura (sin levantar Spring) del servicio de registro.
 */
@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    /** HU 19 - dependencia nueva de ProductoService (registro Premium). */
    @Mock
    private PlanPremiumService planPremiumService;

    @InjectMocks
    private ProductoService productoService;

    @Test
    void debeRegistrarProductoYLlevarId() {
        ProductoRequest request = new ProductoRequest();
        request.setNombre("Mouse inalámbrico");
        request.setDescripcion("Mouse óptico sin cables");
        request.setPrecio(new BigDecimal("75.00"));
        request.setStock(30);
        request.setCategoria("Accesorios");
        request.setEstado("DISPONIBLE");
        request.setImagenUrl("https://example.com/mouse.png");
        request.setVendedorId(2L);

        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto guardado = invocation.getArgument(0);
            guardado.setId(1L);
            return guardado;
        });

        Producto resultado = productoService.registrar(request);

        assertNotNull(resultado.getId());
        assertEquals("Mouse inalámbrico", resultado.getNombre());
        assertEquals(new BigDecimal("75.00"), resultado.getPrecio());
        assertEquals(30, resultado.getStock());
        assertEquals(2L, resultado.getVendedorId());
    }
}
