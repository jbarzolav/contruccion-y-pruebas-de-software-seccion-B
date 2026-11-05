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
 */
@Service
public class EstadoPedidoService {

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

        LocalDateTime ahora = LocalDateTime.now();
        pedido.setEstado(destino.name());
        pedido.setFechaActualizacionEstado(ahora);
        pedidos.save(pedido);

        HistorialEstadoPedido cambio = historial.save(
                new HistorialEstadoPedido(pedido, origen.name(), destino.name(), ahora, vendedor.getId()));
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
