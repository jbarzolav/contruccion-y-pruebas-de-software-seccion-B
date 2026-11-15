package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.DetallePedidoResponse;
import com.ejemplo.backenduserapi.dto.PedidoResponse;
import com.ejemplo.backenduserapi.entity.Carrito;
import com.ejemplo.backenduserapi.entity.DetallePedido;
import com.ejemplo.backenduserapi.entity.IntentoCompra;
import com.ejemplo.backenduserapi.entity.ItemCarrito;
import com.ejemplo.backenduserapi.entity.Pedido;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.exception.ExperienciaCompraException;
import com.ejemplo.backenduserapi.exception.PedidoInvalidoException;
import com.ejemplo.backenduserapi.exception.StockInsuficienteException;
import com.ejemplo.backenduserapi.repository.CarritoRepository;
import com.ejemplo.backenduserapi.repository.IntentoCompraRepository;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
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
    private final NotificacionService notificaciones;
    /** HU 58 - Avisos de stock bajo: se disparan desde la venta. */
    private final AlertaStockService alertas;
    /** HU 59.2 - Clave de intento: asocia un reenvío al pedido ya registrado. */
    private final IntentoCompraRepository intentos;

    public PedidoService(PedidoRepository pedidoRepository,
                         CarritoRepository carritoRepository,
                         ItemCarritoRepository itemCarritoRepository,
                         ProductoRepository productoRepository,
                         NotificacionService notificaciones,
                         AlertaStockService alertas,
                         IntentoCompraRepository intentos) {
        this.pedidoRepository = pedidoRepository;
        this.carritoRepository = carritoRepository;
        this.itemCarritoRepository = itemCarritoRepository;
        this.productoRepository = productoRepository;
        this.notificaciones = notificaciones;
        this.alertas = alertas;
        this.intentos = intentos;
    }

    /** Compatibilidad: compra sin cabecera de idempotencia (cliente viejo). */
    @Transactional
    public PedidoResponse confirmarCompra(Long clienteId) {
        return confirmarCompra(clienteId, null);
    }

    /**
     * HU 59.2 - Confirmar la compra con identificador de intento.
     *
     * Si la clave ya está asociada a un pedido de ESTE cliente (el 201 se
     * perdió en la red y el cliente reenvía), se devuelve el mismo comprobante
     * sin repetir el pedido, sin descontar stock y sin emitir avisos nuevos.
     */
    @Transactional
    public PedidoResponse confirmarCompra(Long clienteId, String claveIntento) {

        Long cliente = resolverCliente(clienteId);

        // Reenvío con la misma clave -> se recupera el pedido original.
        Pedido previo = pedidoDeIntento(claveIntento, cliente);
        if (previo != null) {
            return toResponse(previo);
        }

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
            // HU 58 - La lectura del stock se hace con bloqueo de escritura y
            // refrescando la caché: dos ventas simultáneas no pueden descontar
            // sobre una copia obsoleta ni duplicar el aviso de stock bajo.
            Producto producto = productoConStockBloqueado(item.getProducto());
            if (item.getCantidad() > producto.getStock()) {
                throw new StockInsuficienteException(
                        "Stock insuficiente para \"" + producto.getNombre()
                                + "\": se solicitan " + item.getCantidad()
                                + " y hay " + producto.getStock() + " disponibles");
            }
            int stockAnterior = producto.getStock();
            int stockNuevo = stockAnterior - item.getCantidad();

            producto.setStock(stockNuevo);
            productoRepository.save(producto);
            // HU 58 - Punto unico de deteccion del cruce del umbral.
            alertas.registrarCambioStock(producto, stockAnterior, stockNuevo);

            DetallePedido detalle = new DetallePedido(
                    pedido, producto, item.getCantidad(), item.getPrecioUnitario());
            pedido.getDetalles().add(detalle);

            total = total.add(detalle.getSubtotal());
        }

        pedido.setTotal(total);
        Pedido guardado = pedidoRepository.save(pedido);
        // HU 59.2 - La clave queda asociada al pedido en la MISMA transacción:
        // si algo falla después, el reenvío no encuentra nada que repetir.
        if (claveIntento != null && !claveIntento.isBlank()) {
            intentos.save(new IntentoCompra(claveIntento, cliente, guardado.getId(), LocalDateTime.now()));
        }
        // HU 52 - La compra confirmada avisa a cada vendedor con productos en el pedido.
        notificaciones.notificarNuevaCompra(guardado);

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

    /**
     * HU 59.2 - Pedido ya registrado con esta clave de intento, o null si la
     * clave es nueva (o no se envió). Una clave usada por otra cuenta no
     * revela el pedido ajeno: responde 409.
     */
    private Pedido pedidoDeIntento(String claveIntento, Long cliente) {
        if (claveIntento == null || claveIntento.isBlank()) {
            return null;
        }
        IntentoCompra intento = intentos.findByClave(claveIntento).orElse(null);
        if (intento == null) {
            return null;
        }
        if (!cliente.equals(intento.getClienteId())) {
            throw new ExperienciaCompraException(HttpStatus.CONFLICT,
                    "La clave de intento ya fue usada por otra cuenta.");
        }
        return pedidoRepository.findById(intento.getIdPedido())
                .orElseThrow(() -> new PedidoInvalidoException(
                        "El pedido registrado con esta clave ya no existe"));
    }

    /**
     * HU 58 - Devuelve la publicación con el stock vigente en base de datos.
     * Si la búsqueda bloqueada no devuelve nada (por ejemplo en una prueba con
     * dobles) se conserva la instancia ya cargada del carrito.
     */
    private Producto productoConStockBloqueado(Producto producto) {
        if (producto == null || producto.getId() == null) {
            return producto;
        }
        return productoRepository.buscarParaActualizar(producto.getId()).orElse(producto);
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
                pedido.getEstado(), pedido.getTotal(), detalles);
    }
}
