package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ActualizarPerfilRequest;
import com.ejemplo.backenduserapi.dto.PerfilUsuarioResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.exception.CorreoYaRegistradoException;
import com.ejemplo.backenduserapi.exception.PerfilInvalidoException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * HU 28 - Edición de datos personales del usuario autenticado.
 *
 * La identidad proviene de la sesión (nunca del body): solo se actualizan
 * nombre, correo y teléfono sobre la entidad existente. Rol, permisos,
 * contraseña, hash e id quedan fuera del alcance del servicio.
 */
@Service
public class PerfilService {

    /** Dígitos con opcional "+", espacios o guiones; 7-15 dígitos en total. */
    static final String PATRON_TELEFONO = "^\\+?[0-9][0-9 \\-]{5,17}[0-9]$";

    private final UsuarioRepository usuarios;

    public PerfilService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional
    public PerfilUsuarioResponse actualizar(Usuario usuarioSesion, ActualizarPerfilRequest solicitud) {
        // La cuenta se recarga por id de sesión: si desaparece, la sesión deja de ser válida.
        Usuario usuario = usuarios.findById(usuarioSesion.getId())
                .orElseThrow(() -> new AutorizacionException(HttpStatus.UNAUTHORIZED, "La sesión es inválida o ha vencido."));

        String nombre = solicitud.getNombre() == null ? "" : solicitud.getNombre().trim();
        String correo = solicitud.getCorreo() == null ? "" : solicitud.getCorreo().trim().toLowerCase(Locale.ROOT);
        String telefono = solicitud.getTelefono() == null ? "" : solicitud.getTelefono().trim();

        if (nombre.isEmpty()) {
            throw new PerfilInvalidoException("El nombre es obligatorio.");
        }
        if (correo.isEmpty()) {
            throw new PerfilInvalidoException("El correo es obligatorio.");
        }
        if (!correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new PerfilInvalidoException("El correo debe tener un formato válido.");
        }
        if (!telefono.isEmpty() && !telefono.matches(PATRON_TELEFONO)) {
            throw new PerfilInvalidoException("El teléfono debe contener entre 7 y 15 dígitos (se permite +, espacios y guiones).");
        }

        // Correo ocupado por otra cuenta -> 409 (la propia cuenta sí puede conservarlo).
        usuarios.findByCorreoIgnoreCase(correo)
                .filter(otro -> !otro.getId().equals(usuario.getId()))
                .ifPresent(otro -> {
                    throw new CorreoYaRegistradoException("El correo ya está registrado.");
                });

        // Solo los campos habilitados: id, rol, permisos y passwordHash no se tocan.
        usuario.setNombre(nombre);
        usuario.setCorreo(correo);
        usuario.setTelefono(telefono.isEmpty() ? null : telefono);

        return PerfilUsuarioResponse.de(usuarios.save(usuario));
    }
}
