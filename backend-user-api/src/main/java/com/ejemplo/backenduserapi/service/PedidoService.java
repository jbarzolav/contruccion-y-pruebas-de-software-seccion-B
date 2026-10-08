package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.DetallePedidoResponse;
import com.ejemplo.backenduserapi.dto.PedidoResponse;
import com.ejemplo.backenduserapi.entity.Carrito;
import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.ItemCarrito;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.exception.PedidoInvalidoException;
import com.ejemplo.backenduserapi.exception.StockInsuficienteException;
import com.ejemplo.backenduserapi.repository.CarritoRepository;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * HU 15 - "Confirmar mi compra": convierte el carrito activo en un pedido.
 *
 * Reglas de negocio:
 *  - El carrito no puede estar vacío (400).
 *  - El stock suficiente se valida contra la BD ANTES de descontar nada
 *    (sin descuentos parciales): si algo falla, no se registra el pedido.
 *  - El stock consumido se descuenta de cada producto.
 *  - El carrito se vacía (ítems + carrito) tras registrar el pedido.
 *  - El total lo calcula SIEMPRE el servidor (suma de subtotales).
 */
@Service
public class PedidoService {

    private static final Long CLIENTE_DEFECTO = 1L;

    private final PedidoRepository pedidoRepository;
    private final CarritoRepository carritoRepository;
    private final ItemCarritoRepository itemCarritoRepository;
    private final ProductoRepository productoRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         CarritoRepository carritoRepository,
                         ItemCarritoRepository itemCarritoRepository,
                         ProductoRepository productoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.carritoRepository = carritoRepository;
        this.itemCarritoRepository = itemCarritoRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional
    public PedidoResponse confirmarCompra(Long clienteId) {

        Long cliente = resolverCliente(clienteId);

        Carrito carrito = carritoRepository.findByClienteId(cliente)
                .orElseThrow(() -> new PedidoInvalidoException("El carrito está vacío"));

        List<ItemCarrito> items = itemCarritoRepository.findByCarritoId(carrito.getId());

        if (items.isEmpty()) {
            throw new PedidoInvalidoException("El carrito está vacío");
        }

        // 1) Validar stock de TODOS los ítems antes de descontar nada.
        for (ItemCarrito item : items) {
            int stockActual = item.getProducto().getStock();
            if (item.getCantidad() > stockActual) {
                throw new StockInsuficienteException(
                        "Stock insuficiente para \"" + item.getProducto().getNombre()
                                + "\": se solicitan " + item.getCantidad()
                                + " y hay " + stockActual + " disponibles");
            }
        }

        // 2) Registrar el pedido con el precio congelado del ítem del carrito
        //    y descontar el stock consumido.
        Pedido pedido = new Pedido(cliente, LocalDateTime.now());
        BigDecimal total = BigDecimal.ZERO;

        for (ItemCarrito item : items) {
            Producto producto = item.getProducto();

            producto.setStock(producto.getStock() - item.getCantidad());
            productoRepository.save(producto);

            DetallePedido detalle = new DetallePedido(
                    pedido, producto, item.getCantidad(), item.getPrecioUnitario());
            pedido.getDetalles().add(detalle);

            total = total.add(detalle.getSubtotal());
        }

        pedido.setTotal(total);
        Pedido guardado = pedidoRepository.save(pedido);

        // 3) Vaciar el carrito (ítems + carrito) tras el registro.
        itemCarritoRepository.deleteAll(items);
        carritoRepository.delete(carrito);

        return toResponse(guardado);
    }

    private Long resolverCliente(Long clienteId) {
        if (clienteId != null && clienteId < 1) {
            throw new PedidoInvalidoException("El clienteId debe ser mayor a 0");
        }
        return (clienteId == null) ? CLIENTE_DEFECTO : clienteId;
    }

    private PedidoResponse toResponse(Pedido pedido) {

        List<DetallePedidoResponse> detalles = new ArrayList<>();
        for (DetallePedido detalle : pedido.getDetalles()) {
            detalles.add(new DetallePedidoResponse(
                    detalle.getProducto().getId(),
                    detalle.getProducto().getNombre(),
                    detalle.getCantidad(),
                    detalle.getPrecioUnitario(),
                    detalle.getSubtotal()));
        }

        return new PedidoResponse(
                pedido.getId(), pedido.getClienteId(), pedido.getFecha(),
                pedido.getTotal(), detalles);
    }
}
