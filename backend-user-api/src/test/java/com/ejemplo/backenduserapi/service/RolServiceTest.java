package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** HU 23 - Asignación válida, roles desconocidos y elevación de privilegios. */
@ExtendWith(MockitoExtension.class)
class RolServiceTest {
    @Mock private UsuarioRepository usuarios;
    private RolService servicio;
    private Usuario administrador;
    private Usuario cliente;

    @BeforeEach
    void configurar() {
        servicio = new RolService(usuarios, new AutorizacionService(new TokenService(), usuarios));
        administrador = new Usuario("admin@prueba.com", "hash-admin");
        administrador.setId(1L);
        administrador.setRol(Rol.ADMINISTRADOR);
        cliente = new Usuario("cliente@prueba.com", "hash-cliente");
        cliente.setId(2L);
    }

    @Test
    void asignacionValidaConservaDatosDeLaCuenta() {
        when(usuarios.findById(2L)).thenReturn(Optional.of(cliente));
        when(usuarios.save(cliente)).thenReturn(cliente);
        var respuesta = servicio.asignar(administrador, 2L, "VENDEDOR");
        assertEquals(Rol.VENDEDOR, respuesta.rol());
        assertTrue(respuesta.permisos().contains("GESTIONAR_PRODUCTOS"));
        assertEquals("hash-cliente", cliente.getPasswordHash());
        assertEquals("cliente@prueba.com", respuesta.correo());
    }

    @Test
    void rolDesconocidoNoModificaUsuario() {
        var error = assertThrows(AutorizacionException.class,
                () -> servicio.asignar(administrador, 2L, "SUPERUSUARIO"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getEstado());
        verifyNoInteractions(usuarios);
        assertEquals(Rol.CLIENTE, cliente.getRol());
    }

    @Test
    void clienteNoPuedeElevarSusPrivilegios() {
        var error = assertThrows(AutorizacionException.class,
                () -> servicio.asignar(cliente, 2L, "ADMINISTRADOR"));
        assertEquals(HttpStatus.FORBIDDEN, error.getEstado());
        assertEquals(Rol.CLIENTE, cliente.getRol());
        verify(usuarios, never()).save(any());
    }

    @Test
    void usuarioInexistenteDevuelve404SinGuardar() {
        when(usuarios.findById(99L)).thenReturn(Optional.empty());
        var error = assertThrows(AutorizacionException.class,
                () -> servicio.asignar(administrador, 99L, "CLIENTE"));
        assertEquals(HttpStatus.NOT_FOUND, error.getEstado());
        verify(usuarios, never()).save(any());
    }

    @Test
    void vendedorNoPuedeConsultarUsuariosAdministrativos() {
        cliente.setRol(Rol.VENDEDOR);
        var error = assertThrows(AutorizacionException.class, () -> servicio.listar(cliente));
        assertEquals(HttpStatus.FORBIDDEN, error.getEstado());
        verifyNoInteractions(usuarios);
    }
}
