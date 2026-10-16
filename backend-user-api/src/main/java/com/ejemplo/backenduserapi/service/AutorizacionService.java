package com.ejemplo.backenduserapi.service;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
/** HU 23 - La sesión determina la identidad; el rol se consulta en cada petición. */
@Service
public class AutorizacionService {
    private final TokenService tokens;
    private final UsuarioRepository usuarios;
    public AutorizacionService(TokenService tokens, UsuarioRepository usuarios) { this.tokens = tokens; this.usuarios = usuarios; }
    public Usuario autenticar(String cabecera) {
        if (cabecera == null || !cabecera.startsWith("Bearer "))
            throw new AutorizacionException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión.");
        Long id = tokens.validar(cabecera.substring(7));
        return usuarios.findById(id).orElseThrow(() -> new AutorizacionException(HttpStatus.UNAUTHORIZED, "La sesión es inválida o ha vencido."));
    }
    public void exigirPermiso(Usuario usuario, String permiso) {
        if (!usuario.getRol().getPermisos().contains(permiso))
            throw new AutorizacionException(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción.");
    }
    public void exigirPropietario(Usuario usuario, Long id) {
        if (!usuario.getId().equals(id))
            throw new AutorizacionException(HttpStatus.FORBIDDEN, "No puedes operar con datos de otro usuario.");
    }
}
