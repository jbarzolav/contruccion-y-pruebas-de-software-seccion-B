package com.ejemplo.backenduserapi.repository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
/** HU 23 - Almacenamiento local de sesiones separado de su validación. */
@Repository
public class SesionRepository {
    public record Sesion(Long usuarioId, Instant vencimiento) { }
    private final ConcurrentHashMap<String, Sesion> sesiones = new ConcurrentHashMap<>();
    public void guardar(String token, Long id, Instant vencimiento) { sesiones.put(token, new Sesion(id, vencimiento)); }
    public Optional<Sesion> buscar(String token) { return Optional.ofNullable(sesiones.get(token)); }
    public void eliminar(String token) { sesiones.remove(token); }
}
