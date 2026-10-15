package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.RestablecerRequest;
import com.ejemplo.backenduserapi.entity.TokenRecuperacion;
import com.ejemplo.backenduserapi.exception.RecuperacionInvalidaException;
import com.ejemplo.backenduserapi.repository.TokenRecuperacionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class RecuperacionService {
    public static final String MENSAJE_SOLICITUD =
            "Si el correo está registrado, recibirás un enlace para recuperar tu acceso.";
    private final UsuarioRepository usuarios;
    private final TokenRecuperacionRepository tokens;
    private final PasswordEncoder encoder;
    private final CorreoRecuperacionService correoService;
    private final long minutosVigencia;

    public RecuperacionService(UsuarioRepository usuarios, TokenRecuperacionRepository tokens,
            PasswordEncoder encoder, CorreoRecuperacionService correoService,
            @Value("${app.recuperacion.minutos-vigencia:15}") long minutosVigencia) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.correoService = correoService;
        this.minutosVigencia = minutosVigencia;
    }

    @Transactional
    public void solicitar(String correo) {
        usuarios.findByCorreoIgnoreCase(correo).ifPresent(usuario -> {
            String token = UUID.randomUUID().toString();
            tokens.save(new TokenRecuperacion(token, usuario,
                    Instant.now().plusSeconds(minutosVigencia * 60)));
            correoService.enviar(usuario.getCorreo(), token);
        });
    }

    @Transactional
    public void restablecer(RestablecerRequest solicitud) {
        if (!solicitud.getPassword().equals(solicitud.getConfirmacionPassword())) {
            throw new RecuperacionInvalidaException("Las contraseñas no coinciden.");
        }
        if (solicitud.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new RecuperacionInvalidaException("La contraseña supera el límite de 72 bytes.");
        }
        TokenRecuperacion token = tokens.buscarParaConsumir(solicitud.getToken())
                .filter(t -> t.getVencimiento().isAfter(Instant.now()))
                .orElseThrow(() -> new RecuperacionInvalidaException(
                        "El enlace de recuperación es inválido o ha vencido."));
        var usuario = token.getUsuario();
        usuario.setPasswordHash(encoder.encode(solicitud.getPassword()));
        usuarios.save(usuario);
        tokens.deleteByUsuarioId(usuario.getId());
    }
}
