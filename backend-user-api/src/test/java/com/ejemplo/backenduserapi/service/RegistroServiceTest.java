package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.RegistroRequest;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.CorreoYaRegistradoException;
import com.ejemplo.backenduserapi.exception.RegistroInvalidoException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** HU 26 - Pruebas unitarias del registro: normalización, BCrypt y rol CLIENTE. */
@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    @Mock private UsuarioRepository usuarios;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private RegistroService servicio;

    @BeforeEach
    void configurar() {
        servicio = new RegistroService(usuarios, encoder);
    }

    private RegistroRequest solicitud(String correo, String password, String confirmacion) {
        return new RegistroRequest("Nombre de prueba", correo, password, confirmacion);
    }

    @Test
    void registrarGuardaHashBCryptYAsignaCliente() {
        when(usuarios.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());
        RegistroRequest peticion = solicitud("Nuevo@Pulgatec.com", "Clave123*", "Clave123*");

        servicio.registrar(peticion);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(captor.capture());
        Usuario guardado = captor.getValue();
        // El correo se normaliza y la contraseña nunca se guarda en claro.
        assertEquals("nuevo@pulgatec.com", guardado.getCorreo());
        assertNotEquals("Clave123*", guardado.getPasswordHash());
        assertTrue(guardado.getPasswordHash().startsWith("$2"));
        assertTrue(encoder.matches("Clave123*", guardado.getPasswordHash()));
        assertEquals(Rol.CLIENTE, guardado.getRol());
        assertEquals("Nombre de prueba", guardado.getNombre());
    }

    @Test
    void correoDuplicadoLanza409SinGuardar() {
        Usuario existente = new Usuario("ocupado@pulgatec.com", encoder.encode("Clave123*"));
        when(usuarios.findByCorreoIgnoreCase("ocupado@pulgatec.com"))
                .thenReturn(Optional.of(existente));

        assertThrows(CorreoYaRegistradoException.class,
                () -> servicio.registrar(solicitud("Ocupado@Pulgatec.com", "Clave123*", "Clave123*")));
        verify(usuarios, never()).save(any());
    }

    @Test
    void contrasenasDistintasLanzan400SinConsultarLaBase() {
        assertThrows(RegistroInvalidoException.class,
                () -> servicio.registrar(solicitud("nuevo@pulgatec.com", "Clave123*", "Otra123*")));
        verifyNoInteractions(usuarios);
    }

    @Test
    void passwordMayorA72BytesLanza400() {
        String larga = "a".repeat(73);
        assertThrows(RegistroInvalidoException.class,
                () -> servicio.registrar(solicitud("nuevo@pulgatec.com", larga, larga)));
        verifyNoInteractions(usuarios);
    }
}
