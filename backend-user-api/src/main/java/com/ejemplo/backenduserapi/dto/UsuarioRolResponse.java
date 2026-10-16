package com.ejemplo.backenduserapi.dto;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import java.util.Set;
/** HU 23 - Datos del usuario sin contraseña ni hash. */
public record UsuarioRolResponse(Long id, String correo, Rol rol, Set<String> permisos) {
    public static UsuarioRolResponse de(Usuario u) {
        return new UsuarioRolResponse(u.getId(), u.getCorreo(), u.getRol(), u.getRol().getPermisos());
    }
}
