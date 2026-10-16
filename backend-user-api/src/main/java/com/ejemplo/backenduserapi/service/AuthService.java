package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.LoginRequest;
import com.ejemplo.backenduserapi.dto.LoginResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.CredencialesInvalidasException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * HU 21 - Autenticación: verifica las credenciales con BCrypt y
 * devuelve un token opaco de sesión.
 */
@Service
public class AuthService {

    /**
     * Mismo mensaje para "usuario inexistente" y "contraseña
     * incorrecta": no se revela cuál de los dos falló.
     */
    public static final String MENSAJE_CREDENCIALES_INVALIDAS =
            "Correo o contraseña incorrectos.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest solicitud) {
        Optional<Usuario> usuario =
                usuarioRepository.findByCorreoIgnoreCase(solicitud.getCorreo());

        // La verificación usa BCrypt (matches); jamás equals sobre el hash
        boolean credencialesCorrectas = usuario
                .map(u -> passwordEncoder.matches(solicitud.getPassword(),
                        u.getPasswordHash()))
                .orElse(false);

        if (!credencialesCorrectas) {
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        Usuario existente = usuario.get();
        String token = tokenService.generarToken(existente.getId());

        LoginResponse respuesta = new LoginResponse(token, existente.getId(), existente.getCorreo());
        respuesta.setRol(existente.getRol());
        return respuesta;
    }
}
