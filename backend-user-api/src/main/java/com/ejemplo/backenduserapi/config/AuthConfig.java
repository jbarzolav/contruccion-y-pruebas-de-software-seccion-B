package com.ejemplo.backenduserapi.config;

import org.springframework.beans.factory.annotation.Value;
import com.ejemplo.backenduserapi.entity.Rol;
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
 * starter de seguridad. HU 23 registra la autorización con un interceptor MVC.
 */
@Configuration
public class AuthConfig {

    /** HU 23 - Administrador inicial solo cuando se configura expresamente. */
    @Bean
    public ApplicationRunner administradorInicial(UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${app.admin.correo:}") String correo,
            @Value("${app.admin.password:}") String password) {
        return args -> {
            if (!correo.isBlank() && !password.isBlank() && usuarios.findByCorreoIgnoreCase(correo).isEmpty()) {
                Usuario administrador = new Usuario(correo, encoder.encode(password));
                administrador.setRol(Rol.ADMINISTRADOR);
                usuarios.save(administrador);
            }
        };
    }

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
