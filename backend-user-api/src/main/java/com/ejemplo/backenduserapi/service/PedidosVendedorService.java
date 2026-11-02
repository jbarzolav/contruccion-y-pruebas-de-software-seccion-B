package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.DetallePedidoResponse;
import com.ejemplo.backenduserapi.dto.PedidoVendedorResponse;
import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** HU 48 - Consulta segura de pedidos recibidos por el vendedor. */
@Service
public class PedidosVendedorService {
    private final PedidoRepository pedidos;

    public PedidosVendedorService(PedidoRepository pedidos) {
        this.pedidos = pedidos;
    }

    @Transactional(readOnly = true)
    public List<PedidoVendedorResponse> listarPedidos(Usuario vendedor) {
        if (vendedor == null || vendedor.getRol() != Rol.VENDEDOR) {
            throw new ExperienciaCompraException(HttpStatus.FORBIDDEN,
                    "Solo un vendedor puede consultar sus pedidos recibidos.");
        }
        return pedidos.findPedidosConProductosDeVendedor(vendedor.getId()).stream()
                .map(pedido -> respuestaParaVendedor(pedido, vendedor.getId()))
                .toList();
    }

    private PedidoVendedorResponse respuestaParaVendedor(Pedido pedido, Long vendedorId) {
        List<DetallePedidoResponse> productos = pedido.getDetalles().stream()
                .filter(detalle -> vendedorId.equals(detalle.getProducto().getVendedorId()))
                .map(this::detalleResponse)
                .toList();
        BigDecimal total = productos.stream().map(DetallePedidoResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PedidoVendedorResponse(pedido.getId(), pedido.getFecha(), pedido.getEstado(),
                pedido.getFechaActualizacionEstado(), total, productos);
    }

    private DetallePedidoResponse detalleResponse(DetallePedido detalle) {
        return new DetallePedidoResponse(detalle.getProducto().getId(), detalle.getProducto().getNombre(),
                detalle.getCantidad(), detalle.getPrecioUnitario(), detalle.getSubtotal());
    }
}
