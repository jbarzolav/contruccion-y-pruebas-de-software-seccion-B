package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.NotificacionResponse;
import com.ejemplo.backenduserapi.entity.HistorialEstadoPedido;
import com.ejemplo.backenduserapi.entity.Notificacion;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacionService {

    public static final String CAMBIO_ESTADO_PEDIDO = "CAMBIO_ESTADO_PEDIDO";
    public static final String NUEVA_COMPRA = "NUEVA_COMPRA";

    private final NotificacionRepository notificaciones;

    public NotificacionService(NotificacionRepository notificaciones) {
        this.notificaciones = notificaciones;
    }

    // HU 51 - El cambio válido de estado genera un aviso para el cliente.
    @Transactional
    public void notificarCambioEstado(HistorialEstadoPedido cambio) {
        var pedido = cambio.getPedido();
        String clave = "estado:" + cambio.getId();
        guardarSiNuevo(new Notificacion(CAMBIO_ESTADO_PEDIDO,
                "La compra N.º " + pedido.getId() + " cambió a "
                        + estadoVisible(cambio.getEstadoNuevo()) + ".",
                cambio.getFechaCambio(), pedido.getClienteId(), pedido.getId(), clave));
    }

    private String estadoVisible(String estado) {
        return switch (estado) {
            case "EN_PROCESO" -> "En proceso";
            case "REGISTRADO" -> "Registrado";
            case "ENTREGADO" -> "Entregado";
            case "CANCELADO" -> "Cancelado";
            default -> estado;
        };
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponse> listarPropias(Usuario usuario) {
        return notificaciones.findByDestinatarioIdOrderByFechaDesc(usuario.getId()).stream()
                .map(this::respuesta)
                .toList();
    }

    private void guardarSiNuevo(Notificacion notificacion) {
        if (!notificaciones.existsByClaveEvento(notificacion.getClaveEvento())) {
            notificaciones.save(notificacion);
        }
    }

    private NotificacionResponse respuesta(Notificacion notificacion) {
        String enlace = notificacion.getTipo().equals(CAMBIO_ESTADO_PEDIDO)
                ? "/pedidos/" + notificacion.getPedidoId()
                : "/pedidos-recibidos#pedido-" + notificacion.getPedidoId();
        return new NotificacionResponse(notificacion.getId(), notificacion.getTipo(),
                notificacion.getResumen(), notificacion.getFecha(), notificacion.getEstado(),
                notificacion.getPedidoId(), enlace);
    }
}
