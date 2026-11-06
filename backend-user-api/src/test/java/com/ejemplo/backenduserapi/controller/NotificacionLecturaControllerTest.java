package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Notificacion;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import com.ejemplo.backenduserapi.service.NotificacionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 54 - PATCH /api/notificaciones/{id}/leida y PATCH /api/notificaciones/leidas
 * contra persistencia real (H2): lectura individual, masiva, repetición idempotente,
 * fecha de lectura, propiedad, notificación ajena, inexistente, inválida y sin sesión.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu54leidas;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class NotificacionLecturaControllerTest extends SesionControllerTest {

    private static final long CLIENTE = 60L;
    private static final long OTRO = 61L;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private NotificacionRepository notificaciones;

    private Notificacion avisoPedido;
    private Notificacion avisoMensaje;
    private Notificacion avisoCompra;
    private Notificacion avisoAjeno;

    @BeforeEach
    void preparar() {
        notificaciones.deleteAll();
        avisoPedido = guardar(CLIENTE, NotificacionService.CAMBIO_ESTADO_PEDIDO, "Cambio de estado",
                LocalDateTime.of(2026, 11, 1, 8, 0), 70L, null, "hu54-pedido");
        avisoMensaje = guardar(CLIENTE, NotificacionService.NUEVO_MENSAJE, "Mensaje nuevo",
                LocalDateTime.of(2026, 11, 3, 9, 0), null, 5L, "hu54-mensaje");
        avisoCompra = guardar(CLIENTE, NotificacionService.NUEVA_COMPRA, "Compra recibida",
                LocalDateTime.of(2026, 11, 5, 10, 0), 72L, null, "hu54-compra");
        avisoAjeno = guardar(OTRO, NotificacionService.NUEVO_MENSAJE, "Mensaje de otro usuario",
                LocalDateTime.of(2026, 11, 4, 11, 0), null, 6L, "hu54-ajeno");
    }

    @AfterEach
    void limpiar() {
        notificaciones.deleteAll();
    }

    // ---------------------------------------------------------------
    // 1) Lectura individual
    // ---------------------------------------------------------------
    @Test
    void marcaUnaNotificacionPropiaComoLeidaYPersisteLaFecha() throws Exception {
        mvc.perform(patch("/api/notificaciones/" + avisoPedido.getId() + "/leida")
                        .with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idNotificacion").value(avisoPedido.getId()))
                .andExpect(jsonPath("$.estado").value(NotificacionService.LEIDA))
                .andExpect(jsonPath("$.fechaLectura").isNotEmpty())
                .andExpect(jsonPath("$.pendientes").value(2));

        Notificacion guardada = notificaciones.findById(avisoPedido.getId()).orElseThrow();
        assertEquals(NotificacionService.LEIDA, guardada.getEstado());
        assertNotNull(guardada.getFechaLectura());
        assertEquals("Cambio de estado", guardada.getResumen());
        assertEquals(2, notificaciones.countByDestinatarioIdAndEstado(CLIENTE, NotificacionService.PENDIENTE));
    }

    @Test
    void repetirLaLecturaNoCambiaLaPrimeraFecha() throws Exception {
        String primera = fechaLecturaHttp(avisoPedido.getId());
        LocalDateTime persistida = notificaciones.findById(avisoPedido.getId()).orElseThrow()
                .getFechaLectura();

        String segunda = fechaLecturaHttp(avisoPedido.getId());
        Notificacion trasSegunda = notificaciones.findById(avisoPedido.getId()).orElseThrow();

        assertNotNull(primera);
        assertEquals(NotificacionService.LEIDA, trasSegunda.getEstado());
        // La BD conserva exactamente la fecha de la primera lectura.
        assertEquals(persistida, trasSegunda.getFechaLectura());
        // La API devuelve esa misma fecha persistida (Jackson y Java imprimen
        // los ceros finales distinto: se compara el valor, no el texto).
        assertEquals(persistida, LocalDateTime.parse(segunda));
        assertEquals(2, notificaciones.countByDestinatarioIdAndEstado(CLIENTE, NotificacionService.PENDIENTE));
    }

    @Test
    void laFechaDeLecturaEsPosteriorALaDelAviso() throws Exception {
        mvc.perform(patch("/api/notificaciones/" + avisoPedido.getId() + "/leida")
                        .with(comoCliente(CLIENTE)))
                .andExpect(status().isOk());

        Notificacion guardada = notificaciones.findById(avisoPedido.getId()).orElseThrow();
        assertNotNull(guardada.getFechaLectura());
        assertTrue(guardada.getFechaLectura().isAfter(guardada.getFecha()));
    }

    // ---------------------------------------------------------------
    // 2) Lectura masiva
    // ---------------------------------------------------------------
    @Test
    void marcaTodasLasPendientesDelUsuarioSinTocarLasDeOtroUsuario() throws Exception {
        mvc.perform(patch("/api/notificaciones/leidas").with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(3))
                .andExpect(jsonPath("$.pendientes").value(0));

        assertEquals(0, notificaciones.countByDestinatarioIdAndEstado(CLIENTE, NotificacionService.PENDIENTE));
        Notificacion intacto = notificaciones.findById(avisoAjeno.getId()).orElseThrow();
        assertEquals(NotificacionService.PENDIENTE, intacto.getEstado());
        assertNull(intacto.getFechaLectura());
        // El contenido de los avisos no cambia: solo estado y fecha de lectura.
        Notificacion leido = notificaciones.findById(avisoCompra.getId()).orElseThrow();
        assertEquals(NotificacionService.LEIDA, leido.getEstado());
        assertEquals("Compra recibida", leido.getResumen());
        assertNotNull(leido.getFechaLectura());
    }

    @Test
    void repetirLaLecturaMasivaNoVuelveAMarcarNada() throws Exception {
        mvc.perform(patch("/api/notificaciones/leidas").with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(3));

        mvc.perform(patch("/api/notificaciones/leidas").with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(0))
                .andExpect(jsonPath("$.pendientes").value(0));
    }

    // ---------------------------------------------------------------
    // 3) Validación de identificadores, propiedad y sesión
    // ---------------------------------------------------------------
    @Test
    void rechazaIdentificadoresInvalidos() throws Exception {
        mvc.perform(patch("/api/notificaciones/abc/leida").with(comoCliente(CLIENTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parámetro inválido"));
        mvc.perform(patch("/api/notificaciones/0/leida").with(comoCliente(CLIENTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("Identificador inválido."));
        mvc.perform(patch("/api/notificaciones/-3/leida").with(comoCliente(CLIENTE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value("Identificador inválido."));
    }

    @Test
    void rechazaNotificacionInexistente() throws Exception {
        mvc.perform(patch("/api/notificaciones/999999/leida").with(comoCliente(CLIENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messages[0]").value("Notificación no encontrada."));
    }

    @Test
    void rechazaMarcarUnaNotificacionDeOtroUsuario() throws Exception {
        mvc.perform(patch("/api/notificaciones/" + avisoPedido.getId() + "/leida")
                        .with(comoCliente(OTRO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messages[0]").value("La notificación pertenece a otro usuario."));

        Notificacion intacta = notificaciones.findById(avisoPedido.getId()).orElseThrow();
        assertEquals(NotificacionService.PENDIENTE, intacta.getEstado());
        assertNull(intacta.getFechaLectura());
    }

    @Test
    void exigeSesionAutenticada() throws Exception {
        mvc.perform(patch("/api/notificaciones/" + avisoPedido.getId() + "/leida"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Autorización"));
        mvc.perform(patch("/api/notificaciones/leidas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Autorización"));

        assertEquals(NotificacionService.PENDIENTE,
                notificaciones.findById(avisoPedido.getId()).orElseThrow().getEstado());
    }

    /** Ejecuta la lectura individual y devuelve la fecha de lectura tal como la devuelve la API. */
    private String fechaLecturaHttp(long idNotificacion) throws Exception {
        String cuerpo = mvc.perform(patch("/api/notificaciones/" + idNotificacion + "/leida")
                        .with(comoCliente(CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(cuerpo).get("fechaLectura").asText();
    }

    private Notificacion guardar(long destinatario, String tipo, String resumen, LocalDateTime fecha,
            Long pedidoId, Long conversacionId, String clave) {
        return notificaciones.saveAndFlush(new Notificacion(tipo, resumen, fecha, destinatario,
                pedidoId, conversacionId, clave));
    }
}
