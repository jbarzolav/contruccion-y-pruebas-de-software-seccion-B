package com.ejemplo.backenduserapi.repository;

import com.ejemplo.backenduserapi.entity.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByDestinatarioIdOrderByFechaDesc(Long destinatarioId);
    boolean existsByClaveEvento(String claveEvento);

    /**
     * HU53 - Página de la bandeja del destinatario con filtros combinables.
     * El orden es estable (fecha descendente, id como desempate) y la consulta
     * se ejecuta una sola vez por página, sin recorrer notificación por notificación.
     */
    @Query("""
            SELECT n FROM Notificacion n
            WHERE n.destinatarioId = :destinatarioId
              AND n.tipo IN :tipos
              AND n.estado IN :estados
              AND (:desde IS NULL OR n.fecha >= :desde)
              AND (:hasta IS NULL OR n.fecha <= :hasta)
            ORDER BY n.fecha DESC, n.id DESC
            """)
    Page<Notificacion> buscar(@Param("destinatarioId") Long destinatarioId,
                              @Param("tipos") List<String> tipos,
                              @Param("estados") List<String> estados,
                              @Param("desde") LocalDateTime desde,
                              @Param("hasta") LocalDateTime hasta,
                              Pageable pageable);

    /** HU53 - Pendientes del usuario en toda la bandeja, no solo en la página actual. */
    long countByDestinatarioIdAndEstado(Long destinatarioId, String estado);

    /**
     * HU54 - Una sola sentencia UPDATE dentro de la transacción: la lectura masiva
     * es atómica y solo toca el estado y la fecha de las notificaciones del usuario.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notificacion n
            SET n.estado = :estado, n.fechaLectura = :fecha
            WHERE n.destinatarioId = :destinatarioId AND n.estado = :pendiente
            """)
    int marcarTodasComoLeidas(@Param("destinatarioId") Long destinatarioId,
                              @Param("estado") String estado,
                              @Param("pendiente") String pendiente,
                              @Param("fecha") LocalDateTime fecha);
}
