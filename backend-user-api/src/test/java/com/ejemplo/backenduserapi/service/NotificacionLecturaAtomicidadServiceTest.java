package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.Notificacion;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HU 54.3 / HU 54.6 - Prueba de INTEGRACIÓN (Spring Boot + transacción real sobre
 * H2, sin mocks de persistencia) que demuestra el ROLLBACK de la lectura masiva.
 *
 * Método: laActualizacionMasivaSeRevuelveCompletaSiLaTransaccionFallaDespuesDelUpdate.
 *
 * Secuencia:
 *  1) Se preparan varias notificaciones PENDIENTE de un usuario y otras de otro usuario.
 *  2) Dentro de una transacción real se llama al servicio real
 *     (NotificacionService.marcarTodasComoLeidas) y, una vez ejecutado el UPDATE,
 *  3) se provoca una excepción deliberada en esa misma transacción.
 *  4) Tras el rollback se comprueba que las notificaciones propias siguen PENDIENTE,
 *     que sus fechas de lectura no cambiaron y que las de otros usuarios están intactas.
 *  5) Como control, la misma operación sin fallo sí se aplica.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu54atomicidad;DB_CLOSE_DELAY=-1")
class NotificacionLecturaAtomicidadServiceTest {

    private static final long PROPIAS = 70L;
    private static final long AJENAS = 71L;

    @Autowired private NotificacionService servicio;
    @Autowired private NotificacionRepository notificaciones;
    @Autowired private PlatformTransactionManager gestorTransacciones;

    private final Usuario usuario = usuario(PROPIAS);

    private Notificacion propiaPendiente1;
    private Notificacion propiaPendiente2;
    private Notificacion propiaPendiente3;
    private Notificacion propiaLeida;
    private Notificacion ajenaPendiente1;
    private Notificacion ajenaPendiente2;
    private LocalDateTime fechaLecturaPrevia;

    static Usuario usuario(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    @BeforeEach
    void preparar() {
        notificaciones.deleteAll();
        // Tres pendientes propias (fechas y tipos distintos)...
        propiaPendiente1 = guardar(PROPIAS, NotificacionService.CAMBIO_ESTADO_PEDIDO,
                "Cambio de estado del pedido 70", LocalDateTime.of(2026, 11, 1, 8, 0),
                70L, null, false, "hu543-propia-1");
        propiaPendiente2 = guardar(PROPIAS, NotificacionService.NUEVO_MENSAJE,
                "Mensaje nuevo en la consulta 5", LocalDateTime.of(2026, 11, 3, 9, 0),
                null, 5L, false, "hu543-propia-2");
        propiaPendiente3 = guardar(PROPIAS, NotificacionService.NUEVA_COMPRA,
                "Compra 72 recibida", LocalDateTime.of(2026, 11, 5, 10, 0),
                72L, null, false, "hu543-propia-3");
        // ...una propia ya leída con fecha de lectura fija...
        propiaLeida = guardar(PROPIAS, NotificacionService.CAMBIO_ESTADO_PEDIDO,
                "Cambio de estado del pedido 71", LocalDateTime.of(2026, 11, 2, 8, 0),
                71L, null, true, "hu543-propia-leida");
        // ...y dos pendientes de otro usuario.
        ajenaPendiente1 = guardar(AJENAS, NotificacionService.NUEVO_MENSAJE,
                "Mensaje nuevo en la consulta 6", LocalDateTime.of(2026, 11, 4, 11, 0),
                null, 6L, false, "hu543-ajena-1");
        ajenaPendiente2 = guardar(AJENAS, NotificacionService.NUEVA_COMPRA,
                "Compra 73 recibida", LocalDateTime.of(2026, 11, 6, 12, 0),
                73L, null, false, "hu543-ajena-2");

        fechaLecturaPrevia = propiaLeida.getFechaLectura();
        assertNotNull(fechaLecturaPrevia);
        assertEquals(3, notificaciones.countByDestinatarioIdAndEstado(PROPIAS, NotificacionService.PENDIENTE));
        assertEquals(2, notificaciones.countByDestinatarioIdAndEstado(AJENAS, NotificacionService.PENDIENTE));
    }

    @AfterEach
    void limpiar() {
        notificaciones.deleteAll();
    }

    @Test
    void laActualizacionMasivaSeRevuelveCompletaSiLaTransaccionFallaDespuesDelUpdate() {
        var transaccion = new TransactionTemplate(gestorTransacciones);

        // ---------------------------------------------------------------
        // 1) Transacción real: operación masiva real del servicio...
        // ---------------------------------------------------------------
        IllegalStateException fallo = assertThrows(IllegalStateException.class, () ->
                transaccion.executeWithoutResult(estado -> {
                    var resultado = servicio.marcarTodasComoLeidas(usuario);

                    // El UPDATE sí se ejecutó dentro de esta transacción:
                    // afectó a las 3 pendientes propias y dejó el contador en 0.
                    assertEquals(3, resultado.marcadas());
                    assertEquals(0, resultado.pendientes());
                    assertEquals(0, notificaciones.countByDestinatarioIdAndEstado(
                            PROPIAS, NotificacionService.PENDIENTE));

                    // ...y 2) excepción deliberada después del UPDATE, en la MISMA transacción.
                    throw new IllegalStateException("Fallo provocado después del UPDATE (HU 54.3).");
                }));

        assertEquals("Fallo provocado después del UPDATE (HU 54.3).", fallo.getMessage());

        // ---------------------------------------------------------------
        // 3) Tras el rollback: las propias siguen PENDIENTE...
        // ---------------------------------------------------------------
        assertEquals(3, notificaciones.countByDestinatarioIdAndEstado(
                PROPIAS, NotificacionService.PENDIENTE));
        for (Notificacion propia : List.of(propiaPendiente1, propiaPendiente2, propiaPendiente3)) {
            Notificacion guardada = recargar(propia.getId());
            assertEquals(NotificacionService.PENDIENTE, guardada.getEstado());
            // ...y sus fechas de lectura permanecen sin cambios (nunca hubo una).
            assertNull(guardada.getFechaLectura());
            // El contenido del aviso tampoco se altera.
            assertEquals(propia.getResumen(), guardada.getResumen());
            assertEquals(propia.getFecha(), guardada.getFecha());
            assertEquals(propia.getTipo(), guardada.getTipo());
        }

        // La propia ya leída conserva exactamente su fecha de lectura previa.
        Notificacion leidaTrasRollback = recargar(propiaLeida.getId());
        assertEquals(NotificacionService.LEIDA, leidaTrasRollback.getEstado());
        assertEquals(fechaLecturaPrevia, leidaTrasRollback.getFechaLectura());

        // ---------------------------------------------------------------
        // 4) Las notificaciones de otros usuarios tampoco se modificaron.
        // ---------------------------------------------------------------
        assertEquals(2, notificaciones.countByDestinatarioIdAndEstado(AJENAS, NotificacionService.PENDIENTE));
        for (Notificacion ajena : List.of(ajenaPendiente1, ajenaPendiente2)) {
            Notificacion guardada = recargar(ajena.getId());
            assertEquals(NotificacionService.PENDIENTE, guardada.getEstado());
            assertNull(guardada.getFechaLectura());
            assertEquals(ajena.getResumen(), guardada.getResumen());
        }

        // ---------------------------------------------------------------
        // 5) Control: sin el fallo, la misma operación real sí se aplica.
        // ---------------------------------------------------------------
        var control = servicio.marcarTodasComoLeidas(usuario);
        assertEquals(3, control.marcadas());
        assertEquals(0, control.pendientes());
        assertEquals(0, notificaciones.countByDestinatarioIdAndEstado(PROPIAS, NotificacionService.PENDIENTE));
        // Las ajenas siguen intactas también en el caso sin fallo.
        assertEquals(2, notificaciones.countByDestinatarioIdAndEstado(AJENAS, NotificacionService.PENDIENTE));
    }

    /** Relee la notificación desde H2 fuera de la transacción fallida. */
    private Notificacion recargar(Long id) {
        return notificaciones.findById(id).orElseThrow();
    }

    private Notificacion guardar(long destinatario, String tipo, String resumen, LocalDateTime fecha,
            Long pedidoId, Long conversacionId, boolean leida, String clave) {
        Notificacion notificacion = new Notificacion(tipo, resumen, fecha, destinatario, pedidoId,
                conversacionId, clave);
        if (leida) notificacion.marcarLeida(fecha.plusDays(1));
        return notificaciones.saveAndFlush(notificacion);
    }
}
