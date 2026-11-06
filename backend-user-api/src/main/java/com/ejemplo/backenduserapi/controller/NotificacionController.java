package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.BandejaNotificacionesResponse;
import com.ejemplo.backenduserapi.dto.NotificacionLeidaResponse;
import com.ejemplo.backenduserapi.dto.NotificacionResponse;
import com.ejemplo.backenduserapi.dto.NotificacionesLeidasResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.NotificacionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@CrossOrigin(origins = "*")
public class NotificacionController {

    private final NotificacionService notificaciones;

    public NotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping
    public List<NotificacionResponse> listar(HttpServletRequest request) {
        return notificaciones.listarPropias(usuario(request));
    }

    /**
     * HU 53 - Bandeja unificada de mensajes y pedidos con filtros combinables,
     * paginación y contador de pendientes de toda la bandeja.
     *
     * Parámetros: tipo (CAMBIO_ESTADO_PEDIDO | NUEVA_COMPRA | NUEVO_MENSAJE),
     * estado (PENDIENTE | LEIDA), desde y hasta (AAAA-MM-DD o AAAA-MM-DDTHH:MM),
     * page (por defecto 0) y size (por defecto 20, máximo 50).
     */
    @GetMapping("/bandeja")
    public BandejaNotificacionesResponse bandeja(HttpServletRequest request,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificaciones.bandeja(usuario(request), tipo, estado, desde, hasta, page, size);
    }

    /** HU 54 - Marca una notificación propia como leída (idempotente). */
    @PatchMapping("/{idNotificacion}/leida")
    public NotificacionLeidaResponse marcarLeida(HttpServletRequest request,
            @PathVariable Long idNotificacion) {
        return notificaciones.marcarLeida(usuario(request), idNotificacion);
    }

    /** HU 54 - Marca todas las notificaciones pendientes del usuario como leídas. */
    @PatchMapping("/leidas")
    public NotificacionesLeidasResponse marcarTodasComoLeidas(HttpServletRequest request) {
        return notificaciones.marcarTodasComoLeidas(usuario(request));
    }

    private Usuario usuario(HttpServletRequest request) {
        return (Usuario) request.getAttribute("usuarioSesion");
    }
}
