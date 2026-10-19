package com.ejemplo.backenduserapi.dto;

import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;

import java.util.Set;

/**
 * HU 27 / HU 28 - Perfil del usuario autenticado sin contraseña ni hash.
 *
 * Extiende el DTO de HU 27 con los datos personales editables (nombre y
 * teléfono); los campos autorizados de HU 27 se conservan sin cambios.
 */
public record PerfilUsuarioResponse(Long id, String nombre, String correo, String telefono, Rol rol,
                                    Set<String> permisos) {

    public static PerfilUsuarioResponse de(Usuario u) {
        return new PerfilUsuarioResponse(u.getId(), u.getNombre(), u.getCorreo(), u.getTelefono(),
                u.getRol(), u.getRol().getPermisos());
    }
}
