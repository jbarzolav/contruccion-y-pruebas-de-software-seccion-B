package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.BandejaNotificacionesResponse;
import com.ejemplo.backenduserapi.dto.NotificacionLeidaResponse;
import com.ejemplo.backenduserapi.dto.NotificacionResponse;
import com.ejemplo.backenduserapi.dto.NotificacionesLeidasResponse;
import com.ejemplo.backenduserapi.entity.Conversacion;
import com.ejemplo.backenduserapi.entity.HistorialEstadoPedido;
import com.ejemplo.backenduserapi.entity.Mensaje;
import com.ejemplo.backenduserapi.entity.Notificacion;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.repository.NotificacionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class NotificacionService {

    public static final String CAMBIO_ESTADO_PEDIDO = "CAMBIO_ESTADO_PEDIDO";
    public static final String NUEVA_COMPRA = "NUEVA_COMPRA";
    /** HU 53 - Aviso generado por un mensaje nuevo en una conversación. */
    public static final String NUEVO_MENSAJE = "NUEVO_MENSAJE";

    public static final String PENDIENTE = "PENDIENTE";
    public static final String LEIDA = "LEIDA";

    /** HU 53 - Tamaño máximo de página admitido por la bandeja. */
    public static final int TAMANO_MAXIMO = 50;

    private static final List<String> TIPOS_CONOCIDOS =
            List.of(CAMBIO_ESTADO_PEDIDO, NUEVA_COMPRA, NUEVO_MENSAJE);
    private static final List<String> ESTADOS_CONOCIDOS = List.of(PENDIENTE, LEIDA);

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

    // HU 52 - La compra registrada avisa a cada vendedor participante.
    @Transactional
    public void notificarNuevaCompra(com.ejemplo.backenduserapi.entity.Pedido pedido) {
        pedido.getDetalles().stream()
                .map(detalle -> detalle.getProducto().getVendedorId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(vendedorId -> {
                    String clave = "compra:" + pedido.getId() + ":" + vendedorId;
                    guardarSiNuevo(new Notificacion(NUEVA_COMPRA,
                            "Nueva compra recibida · N.º de compra: " + pedido.getId() + ".",
                            pedido.getFecha(), vendedorId, pedido.getId(), clave));
                });
    }

    /**
     * HU 53 - Un solo aviso por mensaje nuevo para el participante que no lo escribió.
     * La clave por mensaje evita avisos duplicados si el evento se procesa dos veces.
     */
    @Transactional
    public void notificarMensaje(Mensaje mensaje) {
        Conversacion conversacion = mensaje.getConversacion();
        boolean escribioElCliente = conversacion.getCliente().getId().equals(mensaje.getEmisor().getId());
        Long destinatarioId = escribioElCliente
                ? conversacion.getVendedor().getId()
                : conversacion.getCliente().getId();
        String resumen = escribioElCliente
                ? "El cliente te envió un mensaje."
                : "El vendedor respondió tu consulta.";
        guardarSiNuevo(new Notificacion(NUEVO_MENSAJE, resumen,
                LocalDateTime.ofInstant(mensaje.getFecha(), ZoneId.systemDefault()),
                destinatarioId, null, conversacion.getId(), "mensaje:" + mensaje.getId()));
    }

    /**
     * HU 53 - Página de la bandeja unificada con filtros combinables, orden estable
     * por fecha y contador de pendientes de toda la bandeja del usuario autenticado.
     */
    @Transactional(readOnly = true)
    public BandejaNotificacionesResponse bandeja(Usuario usuario, String tipo, String estado,
            String desde, String hasta, int pagina, int size) {
        if (usuario == null) throw error(HttpStatus.UNAUTHORIZED, "Sesión requerida.");
        List<String> tipos = (tipo == null || tipo.isBlank()) ? TIPOS_CONOCIDOS : List.of(tipoValido(tipo));
        List<String> estados = (estado == null || estado.isBlank())
                ? ESTADOS_CONOCIDOS : List.of(estadoValido(estado));
        if (pagina < 0) throw error(HttpStatus.BAD_REQUEST, "La página debe ser no negativa.");
        if (size < 1 || size > TAMANO_MAXIMO)
            throw error(HttpStatus.BAD_REQUEST, "El tamaño de la página debe estar entre 1 y " + TAMANO_MAXIMO + ".");
        LocalDateTime desdeFecha = fecha(desde, true);
        LocalDateTime hastaFecha = fecha(hasta, false);
        if (desdeFecha != null && hastaFecha != null && desdeFecha.isAfter(hastaFecha))
            throw error(HttpStatus.BAD_REQUEST, "El rango de fechas no es válido: desde no puede ser posterior a hasta.");

        Page<Notificacion> resultado = notificaciones.buscar(usuario.getId(), tipos, estados,
                desdeFecha, hastaFecha, PageRequest.of(pagina, size));
        long pendientes = notificaciones.countByDestinatarioIdAndEstado(usuario.getId(), PENDIENTE);
        return new BandejaNotificacionesResponse(
                resultado.getContent().stream().map(this::respuesta).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
                resultado.getTotalPages(), resultado.hasNext(), pendientes);
    }

    /**
     * HU 54 - Marca una notificación propia como leída. Es idempotente: una
     * notificación ya leída conserva su primera fecha de lectura.
     */
    @Transactional
    public NotificacionLeidaResponse marcarLeida(Usuario usuario, Long idNotificacion) {
        if (usuario == null) throw error(HttpStatus.UNAUTHORIZED, "Sesión requerida.");
        if (idNotificacion == null || idNotificacion <= 0)
            throw error(HttpStatus.BAD_REQUEST, "Identificador inválido.");
        Notificacion notificacion = notificaciones.findById(idNotificacion)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Notificación no encontrada."));
        if (!usuario.getId().equals(notificacion.getDestinatarioId()))
            throw error(HttpStatus.FORBIDDEN, "La notificación pertenece a otro usuario.");
        notificacion.marcarLeida(LocalDateTime.now());
        Notificacion guardada = notificaciones.save(notificacion);
        long pendientes = notificaciones.countByDestinatarioIdAndEstado(usuario.getId(), PENDIENTE);
        return new NotificacionLeidaResponse(guardada.getId(), guardada.getEstado(),
                guardada.getFechaLectura(), pendientes);
    }

    /**
     * HU 54 - Marca todas las notificaciones pendientes del usuario en una sola
     * sentencia UPDATE dentro de la transacción: o se aplican todas o ninguna.
     */
    @Transactional
    public NotificacionesLeidasResponse marcarTodasComoLeidas(Usuario usuario) {
        if (usuario == null) throw error(HttpStatus.UNAUTHORIZED, "Sesión requerida.");
        LocalDateTime fechaLectura = LocalDateTime.now();
        int marcadas = notificaciones.marcarTodasComoLeidas(usuario.getId(), LEIDA, PENDIENTE, fechaLectura);
        long pendientes = notificaciones.countByDestinatarioIdAndEstado(usuario.getId(), PENDIENTE);
        return new NotificacionesLeidasResponse(marcadas, pendientes);
    }

    private String tipoValido(String tipo) {
        if (!TIPOS_CONOCIDOS.contains(tipo))
            throw error(HttpStatus.BAD_REQUEST, "Tipo de notificación inválido: use "
                    + String.join(", ", TIPOS_CONOCIDOS) + ".");
        return tipo;
    }

    private String estadoValido(String estado) {
        if (!ESTADOS_CONOCIDOS.contains(estado))
            throw error(HttpStatus.BAD_REQUEST, "Estado de notificación inválido: use "
                    + String.join(", ", ESTADOS_CONOCIDOS) + ".");
        return estado;
    }

    /** Acepta fecha completa (AAAA-MM-DDTHH:MM) o día completo (AAAA-MM-DD). */
    private LocalDateTime fecha(String valor, boolean esDesde) {
        if (valor == null || valor.isBlank()) return null;
        try {
            if (valor.length() == 10) {
                LocalDate dia = LocalDate.parse(valor);
                return esDesde ? dia.atStartOfDay() : dia.atTime(LocalTime.MAX);
            }
            return LocalDateTime.parse(valor);
        } catch (DateTimeParseException excepcion) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Fecha inválida: use el formato AAAA-MM-DD o AAAA-MM-DDTHH:MM.");
        }
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

    /** Enlace directo al pedido o a la conversación según el tipo de aviso (HU 53). */
    private NotificacionResponse respuesta(Notificacion notificacion) {
        String enlace = switch (notificacion.getTipo()) {
            case NUEVO_MENSAJE -> "/consultas?c=" + notificacion.getConversacionId();
            case CAMBIO_ESTADO_PEDIDO -> "/pedidos/" + notificacion.getPedidoId();
            default -> "/pedidos-recibidos#pedido-" + notificacion.getPedidoId();
        };
        return new NotificacionResponse(notificacion.getId(), notificacion.getTipo(),
                notificacion.getResumen(), notificacion.getFecha(), notificacion.getEstado(),
                notificacion.getPedidoId(), enlace, notificacion.getConversacionId(),
                notificacion.getFechaLectura());
    }

    private AutorizacionException error(HttpStatus estado, String mensaje) {
        return new AutorizacionException(estado, mensaje);
    }
}
