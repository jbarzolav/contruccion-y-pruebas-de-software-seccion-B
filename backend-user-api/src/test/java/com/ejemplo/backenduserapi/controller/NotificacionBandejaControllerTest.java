package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Notificacion;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import com.ejemplo.backenduserapi.service.NotificacionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU 53 - GET /api/notificaciones/bandeja contra persistencia real (H2):
 * filtros combinados, orden estable, paginación, límites, contador de pendientes,
 * respuesta vacía, aislamiento entre usuarios y sesión ausente.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hu53bandeja;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class NotificacionBandejaControllerTest extends SesionControllerTest {

    private static final long CLIENTE = 50L;
    private static final long OTRO = 51L;

    @Autowired private MockMvc mvc;
    @Autowired private NotificacionRepository notificaciones;

    @BeforeEach
    void preparar() {
        notificaciones.deleteAll();
        // Orden de creación deliberadamente distinto al orden por fecha.
        guardar(CLIENTE, NotificacionService.CAMBIO_ESTADO_PEDIDO, "Cambio de estado del pedido 70",
                LocalDateTime.of(2026, 11, 1, 8, 0), 70L, null, false, "hu53-pedido");
        guardar(CLIENTE, NotificacionService.NUEVO_MENSAJE, "Mensaje nuevo en la consulta 5",
                LocalDateTime.of(2026, 11, 3, 9, 0), null, 5L, false, "hu53-mensaje");
        guardar(CLIENTE, NotificacionService.NUEVA_COMPRA, "Compra 72 recibida",
                LocalDateTime.of(2026, 11, 5, 10, 0), 72L, null, true, "hu53-compra");
        guardar(OTRO, NotificacionService.NUEVO_MENSAJE, "Mensaje nuevo en la consulta 6",
                LocalDateTime.of(2026, 11, 4, 11, 0), null, 6L, false, "hu53-ajeno");
    }

    @AfterEach
    void limpiar() {
        notificaciones.deleteAll();
    }

    // ---------------------------------------------------------------
    // 1) Respuesta paginada, orden y contador
    // ---------------------------------------------------------------
    @Test
    void devuelveLaBandejaPaginadaDelUsuarioAutenticado() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(3)))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.tieneSiguiente").value(false))
                .andExpect(jsonPath("$.pendientes").value(2))
                // Orden estable: fecha descendente.
                .andExpect(jsonPath("$.notificaciones[0].tipo").value(NotificacionService.NUEVA_COMPRA))
                .andExpect(jsonPath("$.notificaciones[1].tipo").value(NotificacionService.NUEVO_MENSAJE))
                .andExpect(jsonPath("$.notificaciones[2].tipo").value(NotificacionService.CAMBIO_ESTADO_PEDIDO));
    }

    @Test
    void enlazaCadaAvisoASuPedidoOConversacion() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones[0].enlace").value("/pedidos-recibidos#pedido-72"))
                .andExpect(jsonPath("$.notificaciones[0].idPedido").value(72))
                .andExpect(jsonPath("$.notificaciones[0].fechaLectura", notNullValue()))
                .andExpect(jsonPath("$.notificaciones[1].enlace").value("/consultas?c=5"))
                .andExpect(jsonPath("$.notificaciones[1].idConversacion").value(5))
                .andExpect(jsonPath("$.notificaciones[2].enlace").value("/pedidos/70"))
                .andExpect(jsonPath("$.notificaciones[2].fechaLectura", nullValue()));
    }

    @Test
    void elContadorDePendientesNoSeLimitaALaPaginaActual() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.tieneSiguiente").value(true))
                .andExpect(jsonPath("$.pendientes").value(2));
    }

    // ---------------------------------------------------------------
    // 2) Filtros combinables
    // ---------------------------------------------------------------
    @Test
    void filtraPorTipo() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("tipo", NotificacionService.NUEVO_MENSAJE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.notificaciones[0].idConversacion").value(5));
    }

    @Test
    void filtraPorEstado() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("estado", NotificacionService.PENDIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(2)))
                .andExpect(jsonPath("$.notificaciones[0].estado").value(NotificacionService.PENDIENTE));

        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("estado", NotificacionService.LEIDA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.notificaciones[0].estado").value(NotificacionService.LEIDA));
    }

    @Test
    void filtraPorRangoDeFecha() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("desde", "2026-11-02").param("hasta", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value(NotificacionService.NUEVO_MENSAJE));

        // El día completo desde las 00:00.
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("desde", "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value(NotificacionService.NUEVA_COMPRA));

        // El día completo hasta las 23:59:59.
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("hasta", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value(NotificacionService.CAMBIO_ESTADO_PEDIDO));
    }

    @Test
    void combinaTipoEstadoYFecha() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("tipo", NotificacionService.NUEVO_MENSAJE)
                        .param("estado", NotificacionService.PENDIENTE)
                        .param("desde", "2026-11-01").param("hasta", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.notificaciones[0].idConversacion").value(5));

        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("tipo", NotificacionService.CAMBIO_ESTADO_PEDIDO)
                        .param("estado", NotificacionService.LEIDA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(0)));
    }

    // ---------------------------------------------------------------
    // 3) Paginación y límites
    // ---------------------------------------------------------------
    @Test
    void navegaEntrePaginas() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(2)))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.tieneSiguiente").value(true));

        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tieneSiguiente").value(false))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value(NotificacionService.CAMBIO_ESTADO_PEDIDO));
    }

    @Test
    void rechazaPaginaYTamanioFueraDeLimites() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("size", "veinte"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void admiteElTamanioMaximoPermitido() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamano").value(50));
    }

    @Test
    void rechazaFechasYRangoDeFechasInvalidos() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("desde", "mañana"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("hasta", "2026-13-45"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("desde", "2026-11-10").param("hasta", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazaTipoYEstadoNoPermitidos() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("tipo", "INVENTADO"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE))
                        .param("estado", "LEIDO"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------
    // 4) Vacío, aislamiento, sesión y desempate
    // ---------------------------------------------------------------
    @Test
    void devuelveListaVaciaCuandoNoHayNotificaciones() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoVendedor(52L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0))
                .andExpect(jsonPath("$.tieneSiguiente").value(false))
                .andExpect(jsonPath("$.pendientes").value(0));
    }

    @Test
    void soloDevuelveLasNotificacionesDelUsuarioSolicitante() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(OTRO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.notificaciones[0].idConversacion").value(6))
                .andExpect(jsonPath("$.pendientes").value(1));
    }

    @Test
    void exigeSesionAutenticada() throws Exception {
        mvc.perform(get("/api/notificaciones/bandeja"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Autorización"));
    }

    @Test
    void desempataPorIdAnteLaMismaFecha() throws Exception {
        notificaciones.deleteAll();
        LocalDateTime empate = LocalDateTime.of(2026, 11, 2, 12, 0);
        Notificacion primera = guardar(CLIENTE, NotificacionService.NUEVO_MENSAJE, "Primer aviso",
                empate, null, 5L, false, "hu53-empate-1");
        Notificacion segunda = guardar(CLIENTE, NotificacionService.NUEVO_MENSAJE, "Segundo aviso",
                empate, null, 6L, false, "hu53-empate-2");

        mvc.perform(get("/api/notificaciones/bandeja").with(comoCliente(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones", hasSize(2)))
                .andExpect(jsonPath("$.notificaciones[0].idNotificacion").value(segunda.getId()))
                .andExpect(jsonPath("$.notificaciones[1].idNotificacion").value(primera.getId()));
    }

    private Notificacion guardar(long destinatario, String tipo, String resumen, LocalDateTime fecha,
            Long pedidoId, Long conversacionId, boolean leida, String clave) {
        Notificacion notificacion = new Notificacion(tipo, resumen, fecha, destinatario, pedidoId,
                conversacionId, clave);
        if (leida) notificacion.marcarLeida(fecha.plusDays(1));
        return notificaciones.saveAndFlush(notificacion);
    }
}
