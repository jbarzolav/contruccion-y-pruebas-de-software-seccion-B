package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.AuthMessageResponse;
import com.ejemplo.backenduserapi.dto.RegistroRequest;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.CorreoYaRegistradoException;
import com.ejemplo.backenduserapi.exception.RegistroInvalidoException;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * HU 26 - Registro de cuenta: normaliza el correo, guarda solo el hash
 * BCrypt y asigna CLIENTE (nunca un rol recibido del cliente).
 */
@Service
public class RegistroService {

    public static final String MENSAJE_CUENTA_CREADA = "Cuenta creada correctamente.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public RegistroService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional
    public AuthMessageResponse registrar(RegistroRequest solicitud) {
        if (!solicitud.getPassword().equals(solicitud.getConfirmacionPassword())) {
            throw new RegistroInvalidoException("Las contraseñas no coinciden.");
        }
        // BCrypt solo considera los primeros 72 bytes: se rechaza antes de codificar.
        if (solicitud.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new RegistroInvalidoException("La contraseña supera el límite de 72 bytes.");
        }
        String correo = solicitud.getCorreo().trim().toLowerCase(Locale.ROOT);
        if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
            throw new CorreoYaRegistradoException("El correo ya está registrado.");
        }
        usuarios.save(new Usuario(correo, encoder.encode(solicitud.getPassword())));
        return new AuthMessageResponse(MENSAJE_CUENTA_CREADA);
    }
}
