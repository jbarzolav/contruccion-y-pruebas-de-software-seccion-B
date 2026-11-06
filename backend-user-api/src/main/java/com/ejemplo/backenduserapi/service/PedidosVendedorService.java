package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.DetallePedidoResponse;
import com.ejemplo.backenduserapi.dto.PedidoVendedorResponse;
import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.EstadoPedido;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

/** HU 48 - Consulta segura de pedidos recibidos por el vendedor. */
@Service
public class PedidosVendedorService {
    private final PedidoRepository pedidos;
    private final ProductoRepository productos;

    public PedidosVendedorService(PedidoRepository pedidos, ProductoRepository productos) {
        this.pedidos = pedidos;
        this.productos = productos;
    }

    @Transactional(readOnly = true)
    public List<PedidoVendedorResponse> listarPedidos(Usuario vendedor) {
        exigirVendedor(vendedor);
        return pedidos.findPedidosConProductosDeVendedor(vendedor.getId()).stream()
                .map(pedido -> respuestaParaVendedor(pedido, vendedor.getId()))
                .toList();
    }

    /** HU 56 - Filtra pedidos y productos del vendedor autenticado. */
    @Transactional(readOnly = true)
    public List<PedidoVendedorResponse> filtrarPedidos(Usuario vendedor, String estado, LocalDate desde,
                                                       LocalDate hasta, Long productoId, Integer pagina,
                                                       Integer tamano) {
        exigirVendedor(vendedor);
        String estadoFiltro = FiltroPedidos.estado(estado);
        FiltroPedidos.validar(desde, hasta, null, null, pagina, tamano);
        if (productoId != null) {
            if (productoId < 1) {
                throw new ExperienciaCompraException(HttpStatus.BAD_REQUEST, "El producto indicado no es válido.");
            }
            var producto = productos.findById(productoId)
                    .orElseThrow(() -> new ExperienciaCompraException(HttpStatus.NOT_FOUND, "Producto no encontrado."));
            if (!vendedor.getId().equals(producto.getVendedorId())) {
                throw new ExperienciaCompraException(HttpStatus.FORBIDDEN,
                        "Solo puedes filtrar por tus propios productos.");
            }
        }
        List<PedidoVendedorResponse> filtrados = pedidos.findPedidosConProductosDeVendedor(vendedor.getId()).stream()
                .map(pedido -> respuestaParaVendedor(pedido, vendedor.getId()))
                .filter(p -> estadoFiltro == null || estadoFiltro.equals(p.estado()))
                .filter(p -> desde == null && hasta == null || FiltroPedidos.fechaEnRango(p.fecha(), desde, hasta))
                .filter(p -> productoId == null || p.productos().stream()
                        .anyMatch(producto -> productoId.equals(producto.getIdProducto())))
                .toList();
        return FiltroPedidos.pagina(filtrados, pagina, tamano);
    }

    private void exigirVendedor(Usuario vendedor) {
        if (vendedor == null || vendedor.getRol() != Rol.VENDEDOR) {
            throw new ExperienciaCompraException(HttpStatus.FORBIDDEN,
                    "Solo un vendedor puede consultar sus pedidos recibidos.");
        }
    }

    private PedidoVendedorResponse respuestaParaVendedor(Pedido pedido, Long vendedorId) {
        List<DetallePedidoResponse> productos = pedido.getDetalles().stream()
                .filter(detalle -> vendedorId.equals(detalle.getProducto().getVendedorId()))
                .map(this::detalleResponse)
                .toList();
        BigDecimal total = productos.stream().map(DetallePedidoResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PedidoVendedorResponse(pedido.getId(), pedido.getFecha(), pedido.getEstado(),
                pedido.getFechaActualizacionEstado(), total, productos,
                EstadoPedido.permitidosDesde(pedido.getEstado()));
    }

    private DetallePedidoResponse detalleResponse(DetallePedido detalle) {
        return new DetallePedidoResponse(detalle.getProducto().getId(), detalle.getProducto().getNombre(),
                detalle.getCantidad(), detalle.getPrecioUnitario(), detalle.getSubtotal());
    }
}
