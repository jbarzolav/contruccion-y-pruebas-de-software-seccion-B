package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ActualizarPerfilRequest;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.exception.CorreoYaRegistradoException;
import com.ejemplo.backenduserapi.exception.PerfilInvalidoException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
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
 * HU 28 - Pruebas unitarias del servicio de edición de perfil:
 * normalización, validaciones, unicidad de correo e identidad de sesión.
 */
@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock private UsuarioRepository usuarios;
    private PerfilService servicio;
    private Usuario sesion;

    @BeforeEach
    void configurar() {
        servicio = new PerfilService(usuarios);
        sesion = new Usuario("cliente@prueba.com", "hash-secreto");
        sesion.setId(7L);
        sesion.setRol(Rol.CLIENTE);
    }

    @Test
    void actualizacionValidaNormalizaYConservaDatosProtegidos() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(sesion));
        when(usuarios.findByCorreoIgnoreCase("nuevo@prueba.com")).thenReturn(Optional.empty());
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = servicio.actualizar(sesion,
                new ActualizarPerfilRequest("  Ana Pérez  ", "  NUEVO@Prueba.com ", "809-555-1234"));

        assertEquals("Ana Pérez", respuesta.nombre());
        assertEquals("nuevo@prueba.com", respuesta.correo());
        assertEquals("809-555-1234", respuesta.telefono());
        assertEquals(Rol.CLIENTE, respuesta.rol());
        assertEquals(7L, respuesta.id());
        // Datos protegidos intactos: hash, rol e id no cambian con el PATCH.
        assertEquals("hash-secreto", sesion.getPasswordHash());
        assertEquals(Rol.CLIENTE, sesion.getRol());
    }

    @Test
    void telefonoVacioLimpiaElValorGuardado() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(sesion));
        when(usuarios.findByCorreoIgnoreCase("cliente@prueba.com")).thenReturn(Optional.of(sesion));
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = servicio.actualizar(sesion,
                new ActualizarPerfilRequest("Ana", "cliente@prueba.com", "   "));

        assertNull(respuesta.telefono());
        assertNull(sesion.getTelefono());
    }

    @Test
    void correoPropioSeConservaSinConflicto() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(sesion));
        when(usuarios.findByCorreoIgnoreCase("cliente@prueba.com")).thenReturn(Optional.of(sesion));
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> servicio.actualizar(sesion,
                new ActualizarPerfilRequest("Ana", "CLIENTE@prueba.com", null)));
    }

    @Test
    void correoDeOtraCuentaDevuelve409SinGuardar() {
        Usuario otra = new Usuario("ocupado@prueba.com", "hash-otra");
        otra.setId(8L);
        when(usuarios.findById(7L)).thenReturn(Optional.of(sesion));
        when(usuarios.findByCorreoIgnoreCase("ocupado@prueba.com")).thenReturn(Optional.of(otra));

        assertThrows(CorreoYaRegistradoException.class, () -> servicio.actualizar(sesion,
                new ActualizarPerfilRequest("Ana", "ocupado@prueba.com", null)));
        verify(usuarios, never()).save(any());
    }

    @Test
    void emailInvalidoDevuelvePerfilInvalido() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(sesion));

        assertThrows(PerfilInvalidoException.class, () -> servicio.actualizar(sesion,
                new ActualizarPerfilRequest("Ana", "no-es-correo", null)));
        verify(usuarios, never()).save(any());
    }

    @Test
    void nombreVacioDevuelvePerfilInvalido() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(sesion));

        assertThrows(PerfilInvalidoException.class, () -> servicio.actualizar(sesion,
                new ActualizarPerfilRequest("   ", "ana@prueba.com", null)));
        verify(usuarios, never()).save(any());
    }

    @Test
    void telefonoConLetrasDevuelvePerfilInvalido() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(sesion));

        assertThrows(PerfilInvalidoException.class, () -> servicio.actualizar(sesion,
                new ActualizarPerfilRequest("Ana", "ana@prueba.com", "calla-mama")));
        verify(usuarios, never()).save(any());
    }

    @Test
    void sesionDeCuentaEliminadaDevuelve401() {
        when(usuarios.findById(7L)).thenReturn(Optional.empty());

        var error = assertThrows(AutorizacionException.class, () -> servicio.actualizar(sesion,
                new ActualizarPerfilRequest("Ana", "ana@prueba.com", null)));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getEstado());
        verify(usuarios, never()).save(any());
    }

    @Test
    void soloSeModificanLosCamposPermitidos() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(sesion));
        when(usuarios.findByCorreoIgnoreCase("cliente@prueba.com")).thenReturn(Optional.of(sesion));
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        servicio.actualizar(sesion, new ActualizarPerfilRequest("Ana", "cliente@prueba.com", "8095551234"));

        // El servicio no expone setters de id/rol/hash y el DTO no los trae: no hay mass assignment.
        assertEquals(7L, sesion.getId());
        assertEquals("hash-secreto", sesion.getPasswordHash());
        assertEquals(Rol.CLIENTE, sesion.getRol());
    }
}
