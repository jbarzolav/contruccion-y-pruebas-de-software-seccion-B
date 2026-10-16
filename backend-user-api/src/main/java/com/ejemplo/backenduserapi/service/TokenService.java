package com.ejemplo.backenduserapi.service;
import com.ejemplo.backenduserapi.repository.SesionRepository;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
/** HU 21 / HU 23 - Sesión opaca asociada al usuario, con vigencia de ocho horas. */
@Service
public class TokenService {
    private final SesionRepository sesiones;
    @Autowired
    public TokenService(SesionRepository sesiones) { this.sesiones = sesiones; }
    // Conserva la construcción usada por las pruebas unitarias de HU21.
    public TokenService() { this(new SesionRepository()); }
    public String generarToken() { return UUID.randomUUID().toString(); }
    public String generarToken(Long usuarioId) {
        String token = generarToken();
        sesiones.guardar(token, usuarioId, Instant.now().plusSeconds(28800));
        return token;
    }
    public Long validar(String token) {
        var sesion = sesiones.buscar(token);
        if (sesion.isEmpty() || !sesion.get().vencimiento().isAfter(Instant.now())) {
            sesiones.eliminar(token);
            throw new AutorizacionException(HttpStatus.UNAUTHORIZED, "La sesión es inválida o ha vencido.");
        }
        return sesion.get().usuarioId();
    }

    /** Invalidación inmediata por logout; el token opaco deja de ser válido en cualquier endpoint. */
    public void invalidar(String token) { sesiones.eliminar(token); }
}
