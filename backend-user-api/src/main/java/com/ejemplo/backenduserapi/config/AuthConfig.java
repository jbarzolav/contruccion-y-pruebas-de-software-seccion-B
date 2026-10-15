package com.ejemplo.backenduserapi.config;

import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.UsuarioRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * HU 21 - Infraestructura de autenticación.
 *
 * Solo se usa spring-security-crypto para BCrypt: NO se activa el
 * starter de seguridad, no hay filtros automáticos ni autorización
 * global; los endpoints del Sprint 1 y de HU 20 siguen abiertos.
 */
@Configuration
public class AuthConfig {

    /** Codificador de contraseñas con BCrypt (hash con salt aleatorio). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * HU 21 - Usuario demo para pruebas manuales:
     * demo@pulgatec.com / Demo123*
     *
     * La contraseña NUNCA se guarda en texto plano: en la tabla solo
     * queda su hash BCrypt. Si el usuario ya existe no se vuelve a
     * crear (idempotente en cada arranque).
     */
    @Bean
    public ApplicationRunner usuarioDemoRunner(UsuarioRepository usuarioRepository,
                                               PasswordEncoder passwordEncoder) {
        return args -> {
            String correoDemo = "demo@pulgatec.com";
            if (usuarioRepository.findByCorreoIgnoreCase(correoDemo).isEmpty()) {
                Usuario demo =
                        new Usuario(correoDemo, passwordEncoder.encode("Demo123*"));
                usuarioRepository.save(demo);
            }
        };
    }
}
