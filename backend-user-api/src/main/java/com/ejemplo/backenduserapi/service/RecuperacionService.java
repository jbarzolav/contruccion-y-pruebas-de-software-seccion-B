package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.RestablecerRequest;
import com.ejemplo.backenduserapi.entity.TokenRecuperacion;
import com.ejemplo.backenduserapi.entity.AccionAuditoria;
import com.ejemplo.backenduserapi.exception.RecuperacionInvalidaException;
import com.ejemplo.backenduserapi.repository.TokenRecuperacionRepository;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

@Service
public class RecuperacionService {
    public static final String MENSAJE_SOLICITUD =
            "Si el correo está registrado, recibirás un código para recuperar tu acceso.";
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final UsuarioRepository usuarios;
    private final TokenRecuperacionRepository tokens;
    private final PasswordEncoder encoder;
    private final CorreoRecuperacionService correoService;
    private final AuditService auditoria;
    private final long minutosVigencia;

    public RecuperacionService(UsuarioRepository usuarios, TokenRecuperacionRepository tokens,
            PasswordEncoder encoder, CorreoRecuperacionService correoService, AuditService auditoria,
            @Value("${app.recuperacion.minutos-vigencia:15}") long minutosVigencia) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.correoService = correoService;
        this.auditoria = auditoria;
        this.minutosVigencia = minutosVigencia;
    }

    @Transactional
    public void solicitar(String correo) {
        usuarios.findByCorreoIgnoreCase(correo).ifPresent(usuario -> {
            String token = generarCodigo();
            tokens.save(new TokenRecuperacion(token, usuario,
                    Instant.now().plusSeconds(minutosVigencia * 60)));
            correoService.enviar(usuario.getCorreo(), token);
        });
    }

    // Código de 6 dígitos enviado por correo; se repite si ya existe otro pendiente.
    private String generarCodigo() {
        String codigo;
        do {
            codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        } while (tokens.existsById(codigo));
        return codigo;
    }

    /**
     * Valida el código sin consumirlo: la contraseña solo se cambia después de
     * que el usuario haya confirmado que el código es correcto.
     */
    @Transactional(readOnly = true)
    public boolean codigoValido(String codigo) {
        if (codigo == null || codigo.isBlank()) return false;
        return tokens.buscarPorToken(codigo.trim())
                .filter(t -> t.getVencimiento().isAfter(Instant.now()))
                .isPresent();
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
                        "El código de recuperación es inválido o ha vencido."));
        var usuario = token.getUsuario();
        usuario.setPasswordHash(encoder.encode(solicitud.getPassword()));
        usuarios.save(usuario);
        tokens.deleteByUsuarioId(usuario.getId());
        auditoria.registrar(usuario, AccionAuditoria.RESTABLECIMIENTO_CONTRASENA);
    }
}
