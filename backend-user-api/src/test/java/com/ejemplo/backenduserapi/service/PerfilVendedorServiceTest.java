package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.PerfilVendedorRequest;
import com.ejemplo.backenduserapi.entity.PerfilVendedor;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.exception.PerfilInvalidoException;
import com.ejemplo.backenduserapi.repository.PerfilVendedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HU 29 - Pruebas unitarias del servicio de perfil comercial del vendedor:
 * guardado, actualización idempotente, validaciones, rol y propiedad.
 */
@ExtendWith(MockitoExtension.class)
class PerfilVendedorServiceTest {

    @Mock private PerfilVendedorRepository perfiles;
    private PerfilVendedorService servicio;
    private Usuario vendedor;

    @BeforeEach
    void configurar() {
        servicio = new PerfilVendedorService(perfiles);
        vendedor = new Usuario("vendedor@prueba.com", "hash-vendedor");
        vendedor.setId(3L);
        vendedor.setRol(Rol.VENDEDOR);
    }

    @Test
    void vendedorGuardaPerfilCorrectamente() {
        when(perfiles.findByUsuarioId(3L)).thenReturn(Optional.empty());
        when(perfiles.save(any(PerfilVendedor.class))).thenAnswer(inv -> {
            PerfilVendedor p = inv.getArgument(0);
            p.getId();
            return p;
        });

        var respuesta = servicio.guardar(vendedor,
                new PerfilVendedorRequest("TecnoAna", "Celulares y accesorios reacondicionados.", "8095551234"));

        assertEquals(3L, respuesta.usuarioId());
        assertEquals("TecnoAna", respuesta.nombreComercial());
        assertEquals("Celulares y accesorios reacondicionados.", respuesta.descripcion());
        assertEquals("8095551234", respuesta.telefonoContacto());
    }

    @Test
    void segundaGuardadaActualizaElMismoPerfilSinDuplicar() {
        PerfilVendedor existente = new PerfilVendedor(3L, "TecnoAna", "Descripción vieja.", "8095551234");
        existente.setId(11L);
        when(perfiles.findByUsuarioId(3L)).thenReturn(Optional.of(existente));
        when(perfiles.save(any(PerfilVendedor.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = servicio.guardar(vendedor,
                new PerfilVendedorRequest("TecnoAna Pro", "Descripción nueva.", "8095559999"));

        assertEquals("TecnoAna Pro", respuesta.nombreComercial());
        assertEquals("Descripción nueva.", existente.getDescripcion());
        // Nunca se crea una segunda fila: se reutiliza la existente.
        verify(perfiles, never()).save(argThat(p -> p != existente));
    }

    @Test
    void clienteEsRechazadoCon403() {
        Usuario cliente = new Usuario("cliente@prueba.com", "hash");
        cliente.setId(4L);
        cliente.setRol(Rol.CLIENTE);

        var error = assertThrows(AutorizacionException.class,
                () -> servicio.guardar(cliente, new PerfilVendedorRequest("X", "Y", "8095551234")));
        assertEquals(HttpStatus.FORBIDDEN, error.getEstado());
        verifyNoInteractions(perfiles);
    }

    @Test
    void administradorNoSeHacePasrPorVendedor() {
        Usuario admin = new Usuario("admin@prueba.com", "hash");
        admin.setId(5L);
        admin.setRol(Rol.ADMINISTRADOR);

        var error = assertThrows(AutorizacionException.class, () -> servicio.obtener(admin));
        assertEquals(HttpStatus.FORBIDDEN, error.getEstado());
        verifyNoInteractions(perfiles);
    }

    @Test
    void elPropietarioEsLaSesionNoElBody() {
        when(perfiles.findByUsuarioId(3L)).thenReturn(Optional.empty());
        when(perfiles.save(any(PerfilVendedor.class))).thenAnswer(inv -> inv.getArgument(0));

        // El DTO no trae usuarioId: la fila siempre se ata al id de la sesión (3L).
        var respuesta = servicio.guardar(vendedor,
                new PerfilVendedorRequest("TecnoAna", "Desc.", "8095551234"));
        assertEquals(3L, respuesta.usuarioId());
    }

    @Test
    void camposVaciosDevuelvenPerfilInvalido() {
        assertThrows(PerfilInvalidoException.class, () -> servicio.guardar(vendedor,
                new PerfilVendedorRequest("   ", "Desc.", "8095551234")));
        assertThrows(PerfilInvalidoException.class, () -> servicio.guardar(vendedor,
                new PerfilVendedorRequest("TecnoAna", "  ", "8095551234")));
        assertThrows(PerfilInvalidoException.class, () -> servicio.guardar(vendedor,
                new PerfilVendedorRequest("TecnoAna", "Desc.", "  ")));
        verify(perfiles, never()).save(any());
    }

    @Test
    void contactoInvalidoDevuelvePerfilInvalido() {
        assertThrows(PerfilInvalidoException.class, () -> servicio.guardar(vendedor,
                new PerfilVendedorRequest("TecnoAna", "Desc.", "no-telefono")));
        verify(perfiles, never()).save(any());
    }

    @Test
    void obtenerSinPerfilDevuelveCamposVacios() {
        when(perfiles.findByUsuarioId(3L)).thenReturn(Optional.empty());

        var respuesta = servicio.obtener(vendedor);

        assertEquals(3L, respuesta.usuarioId());
        assertEquals("", respuesta.nombreComercial());
        assertEquals("", respuesta.telefonoContacto());
    }

    @Test
    void obtenerDevuelveElPerfilPersistido() {
        PerfilVendedor guardado = new PerfilVendedor(3L, "TecnoAna", "Desc.", "8095551234");
        when(perfiles.findByUsuarioId(3L)).thenReturn(Optional.of(guardado));

        var respuesta = servicio.obtener(vendedor);

        assertEquals("TecnoAna", respuesta.nombreComercial());
    }
}
