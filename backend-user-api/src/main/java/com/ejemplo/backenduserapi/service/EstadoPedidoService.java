package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.EstadoPedidoRequest;
import com.ejemplo.backenduserapi.dto.HistorialEstadoPedidoResponse;
import com.ejemplo.backenduserapi.dto.PedidoEstadoActualizadoResponse;
import com.ejemplo.backenduserapi.entity.EstadoPedido;
import com.ejemplo.backenduserapi.entity.HistorialEstadoPedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.HistorialEstadoPedidoRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HU 49 - "Como vendedor, quiero actualizar el estado de un pedido".
 * HU 59.5 / HU 65 - "Como comprador, quiero cancelar mi compra mientras
 * esté registrada".
 *
 * Reglas de negocio:
 *  - Solo un VENDEDOR autenticado puede actualizar el estado (403).
 *  - El pedido debe existir (404) e incluir al menos un producto del
 *    vendedor autenticado (403): nadie actualiza pedidos ajenos.
 *  - El estado destino debe existir en el catálogo de estados (400).
 *  - La transición debe estar permitida por el dominio (400) y no puede
 *    repetir el estado actual (400).
 *  - Cada cambio actualiza el estado y su fecha, y deja una fila en el
 *    historial con el estado anterior, el nuevo, la fecha y el autor.
 *
 * Reglas de la cancelación del comprador (HU 59.5):
 *  - Solo el dueño de la compra puede cancelarla: pedido ajeno → 403,
 *    pedido inexistente → 404.
 *  - Solo cancela mientras el pedido esté en REGISTRADO; cualquier otro
 *    estado → 400 (transición inválida para el comprador).
 *  - El cambio deja historial y genera el aviso de HU 51, igual que el
 *    cambio hecho por el vendedor.
 */
@Service
public class EstadoPedidoService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

    private final PedidoRepository pedidos;
    private final HistorialEstadoPedidoRepository historial;
    private final NotificacionService notificaciones;

    public EstadoPedidoService(PedidoRepository pedidos, HistorialEstadoPedidoRepository historial,
                               NotificacionService notificaciones) {
        this.pedidos = pedidos;
        this.historial = historial;
        this.notificaciones = notificaciones;
    }

    @Transactional
    public PedidoEstadoActualizadoResponse actualizarEstado(Long idPedido, EstadoPedidoRequest request, Usuario vendedor) {
        exigirVendedor(vendedor);

        Pedido pedido = pedidoDelVendedor(idPedido, vendedor);
        if (pedido.getCodigoPedido()!=null) {
            bloquear(pedido, "Solo se puede entregar un pedido pagado; el comprador gestiona su devolución.");
            if (!"PAGADO".equals(pedido.getEstadoPago()) || "EN_TRAMITE".equals(pedido.getEstadoReembolso()) || !"ENTREGADO".equalsIgnoreCase(estadoSolicitado(request)))
                throw error(HttpStatus.CONFLICT,"Solo se puede entregar un pedido pagado; el comprador gestiona su devolución.");
        }

        EstadoPedido destino = EstadoPedido.desde(estadoSolicitado(request));
        if (destino == null) {
            throw error(HttpStatus.BAD_REQUEST,
                    "El estado solicitado no es válido. Estados permitidos: " + String.join(", ", EstadoPedido.todos()) + ".");
        }

        EstadoPedido origen = EstadoPedido.desde(pedido.getEstado());
        if (origen == null) {
            throw error(HttpStatus.BAD_REQUEST, "El estado actual del pedido no es válido.");
        }
        if (origen == destino) {
            throw error(HttpStatus.BAD_REQUEST, "El pedido ya se encuentra en estado " + origen.name() + ".");
        }
        if (!origen.getSiguientes().contains(destino.name())) {
            throw error(HttpStatus.BAD_REQUEST,
                    "No se permite pasar de " + origen.name() + " a " + destino.name() + ".");
        }

        return aplicarCambio(pedido, origen, destino, vendedor);
    }

    /**
     * HU 59.5 / HU 65 - El comprador cancela su propia compra mientras
     * esté registrada.
     *
     *  - 404 si el pedido no existe; 403 si pertenece a otro cliente.
     *  - 400 si ya no está en REGISTRADO (una compra en proceso solo la
     *    cancela el vendedor con PATCH /estado, y ENTREGADO / CANCELADO
     *    son estados finales).
     *  - 200 con el estado nuevo, su fecha y el historial completo.
     */
    @Transactional
    public PedidoEstadoActualizadoResponse cancelarCompra(Long idPedido, Usuario comprador) {
        Pedido pedido = pedidoDelComprador(idPedido, comprador);
        if (pedido.getCodigoPedido()!=null) {
            bloquear(pedido, "El intento está cerrado. Un cobro aprobado requiere reembolso.");
            if (!java.util.Set.of("PENDIENTE","RECHAZADO").contains(pedido.getEstadoPago()))
                throw error(HttpStatus.CONFLICT,"El intento está cerrado. Un cobro aprobado requiere reembolso.");
            pedido.setEstadoPago("CANCELADO");
        }

        EstadoPedido origen = EstadoPedido.desde(pedido.getEstado());
        if (origen == null) {
            throw error(HttpStatus.BAD_REQUEST, "El estado actual del pedido no es válido.");
        }
        if (origen != EstadoPedido.REGISTRADO) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Solo se puede cancelar una compra registrada: el pedido está en estado " + origen.name() + ".");
        }

        return aplicarCambio(pedido, origen, EstadoPedido.CANCELADO, comprador);
    }

    /**
     * Compra que debe ser del comprador autenticado: 404 si no existe y
     * 403 si es de otro cliente (mismo criterio que la experiencia de compra).
     */
    private Pedido pedidoDelComprador(Long idPedido, Usuario comprador) {
        if (idPedido == null || idPedido < 1) {
            throw error(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
        }
        Pedido pedido = pedidos.findById(idPedido)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
        if (comprador == null || !comprador.getId().equals(pedido.getClienteId())) {
            throw error(HttpStatus.FORBIDDEN, "No puedes operar con una compra de otro cliente.");
        }
        return pedido;
    }

    /**
     * Toma el pedido con bloqueo de escritura antes de decidir sobre el pago.
     *
     * Si otra transacción lo modificó mientras se leía (por ejemplo, el
     * cobro aprobándose a la vez que la cancelación), el gestor falla al
     * bloquear: en ese caso se responde con el conflicto de negocio (409)
     * en vez de un error 500, y la transacción se revierte.
     */
    private void bloquear(Pedido pedido, String mensajeConflicto) {
        try {
            em.refresh(pedido, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        } catch (jakarta.persistence.PersistenceException e) {
            throw error(HttpStatus.CONFLICT, mensajeConflicto);
        }
    }

    /** Cambio de estado permitido: persiste, deja historial y avisa (HU 51). */
    private PedidoEstadoActualizadoResponse aplicarCambio(Pedido pedido, EstadoPedido origen,
                                                          EstadoPedido destino, Usuario autor) {
        LocalDateTime ahora = LocalDateTime.now();
        pedido.setEstado(destino.name());
        pedido.setFechaActualizacionEstado(ahora);
        pedidos.save(pedido);

        HistorialEstadoPedido cambio = historial.save(
                new HistorialEstadoPedido(pedido, origen.name(), destino.name(), ahora, autor.getId()));
        // HU 51 - Se avisa solo después de guardar una transición permitida.
        notificaciones.notificarCambioEstado(cambio);

        List<HistorialEstadoPedidoResponse> registros = historial
                .findByPedidoIdOrderByFechaCambioDesc(pedido.getId()).stream()
                .map(this::historialResponse)
                .toList();

        return new PedidoEstadoActualizadoResponse(pedido.getId(), origen.name(), destino.name(), ahora,
                destino.getSiguientes(), registros);
    }

    private Pedido pedidoDelVendedor(Long idPedido, Usuario vendedor) {
        if (idPedido == null || idPedido < 1) {
            throw error(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
        }
        Pedido pedido = pedidos.findById(idPedido)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Pedido no encontrado."));
        boolean incluyeSusProductos = pedido.getDetalles().stream()
                .anyMatch(detalle -> vendedor.getId().equals(detalle.getProducto().getVendedorId()));
        if (!incluyeSusProductos) {
            throw error(HttpStatus.FORBIDDEN, "El pedido no incluye productos de tus publicaciones.");
        }
        return pedido;
    }

    private void exigirVendedor(Usuario vendedor) {
        if (vendedor == null || vendedor.getRol() != Rol.VENDEDOR) {
            throw error(HttpStatus.FORBIDDEN, "Solo un vendedor puede actualizar el estado de un pedido.");
        }
    }

    private String estadoSolicitado(EstadoPedidoRequest request) {
        return (request == null || request.getEstado() == null) ? "" : request.getEstado();
    }

    private HistorialEstadoPedidoResponse historialResponse(HistorialEstadoPedido registro) {
        return new HistorialEstadoPedidoResponse(registro.getPedido().getId(), registro.getEstadoAnterior(),
                registro.getEstadoNuevo(), registro.getFechaCambio(), registro.getUsuarioId());
    }

    private ExperienciaCompraException error(HttpStatus estado, String mensaje) {
        return new ExperienciaCompraException(estado, mensaje);
    }
}
