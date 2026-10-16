package com.ejemplo.backenduserapi.service;
import com.ejemplo.backenduserapi.dto.UsuarioRolResponse;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
/** HU 23 - Administración de roles separada del almacenamiento de usuarios. */
@Service
public class RolService {
    private final UsuarioRepository usuarios;
    private final AutorizacionService autorizacion;
    public RolService(UsuarioRepository usuarios, AutorizacionService autorizacion) { this.usuarios = usuarios; this.autorizacion = autorizacion; }
    public List<UsuarioRolResponse> listar(Usuario administrador) {
        autorizacion.exigirPermiso(administrador, "ASIGNAR_ROLES");
        return usuarios.findAll().stream().map(UsuarioRolResponse::de).toList();
    }
    @Transactional
    public UsuarioRolResponse asignar(Usuario administrador, Long id, String nombreRol) {
        autorizacion.exigirPermiso(administrador, "ASIGNAR_ROLES");
        Rol rol;
        try { rol = Rol.valueOf(nombreRol); }
        catch (IllegalArgumentException ex) { throw new AutorizacionException(HttpStatus.BAD_REQUEST, "El rol debe ser CLIENTE, VENDEDOR o ADMINISTRADOR."); }
        Usuario usuario = usuarios.findById(id).orElseThrow(() -> new AutorizacionException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
        usuario.setRol(rol);
        return UsuarioRolResponse.de(usuarios.save(usuario));
    }
}
