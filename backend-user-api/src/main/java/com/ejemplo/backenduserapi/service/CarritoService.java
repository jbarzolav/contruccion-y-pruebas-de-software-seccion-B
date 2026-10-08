package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ItemCarritoCantidadRequest;
import com.ejemplo.backenduserapi.dto.ItemCarritoRequest;
import com.ejemplo.backenduserapi.dto.ItemCarritoResponse;
import com.ejemplo.backenduserapi.entity.Carrito;
import com.ejemplo.backenduserapi.entity.ItemCarrito;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.exception.ItemCarritoInvalidoException;
import com.ejemplo.backenduserapi.exception.ItemCarritoNoEncontradoException;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.repository.CarritoRepository;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * HU 11 / HU 12 - Carrito de compras.
 *
 * Reglas de negocio:
 *  - La cantidad debe ser al menos 1 (también lo valida Jakarta en los DTOs).
 *  - La cantidad nunca puede superar el stock actual en la BD -> 400.
 *  - El subtotal lo recalcula SIEMPRE el servidor: cantidad * precioUnitario.
 *  - Sin autenticación todavía: clienteId = 1 (misma regla que vendedorId).
 */
@Service
public class CarritoService {

    private static final Long CLIENTE_DEFECTO = 1L;

    private final CarritoRepository carritoRepository;
    private final ItemCarritoRepository itemCarritoRepository;
    private final ProductoRepository productoRepository;

    public CarritoService(CarritoRepository carritoRepository,
                          ItemCarritoRepository itemCarritoRepository,
                          ProductoRepository productoRepository) {
        this.carritoRepository = carritoRepository;
        this.itemCarritoRepository = itemCarritoRepository;
        this.productoRepository = productoRepository;
    }

    // ------------------------------------------------------------------
    // HU 11 - Agregar un producto al carrito
    // POST /api/carrito/items  { productoId, cantidad }  -> 201
    // ------------------------------------------------------------------
    public ItemCarritoResponse agregarItem(Long clienteId, ItemCarritoRequest request) {

        Producto producto = productoRepository.findById(request.getProductoId())
                .orElseThrow(() -> new ProductoNoEncontradoException(
                        "Producto no encontrado con id " + request.getProductoId()));

        validarCantidadContraStock(request.getCantidad(), producto.getStock());

        Carrito carrito = buscarOCrearCarrito(clienteId);

        // El precioUnitario se "congela" al momento de agregar (snapshot).
        ItemCarrito item = new ItemCarrito(carrito, producto,
                request.getCantidad(), producto.getPrecio());

        return toResponse(itemCarritoRepository.save(item));
    }

    // ------------------------------------------------------------------
    // HU 12 - Modificar la cantidad de un ítem del carrito
    // PUT /api/carrito/items/{idItemCarrito}  { cantidad }  ->200
    // ------------------------------------------------------------------
    public ItemCarritoResponse actualizarCantidad(Long idItemCarrito, ItemCarritoCantidadRequest request) {

        ItemCarrito item = itemCarritoRepository.findById(idItemCarrito)
                .orElseThrow(() -> new ItemCarritoNoEncontradoException(
                        "Ítem de carrito no encontrado con id " + idItemCarrito));

        // Se valida de nuevo contra el stock ACTUAL de la BD.
        validarCantidadContraStock(request.getCantidad(), item.getProducto().getStock());

        item.setCantidad(request.getCantidad());

        // El save dispara el recálculo del subtotal en toResponse().
        return toResponse(itemCarritoRepository.save(item));
    }

    // ------------------------------------------------------------------
    // Lectura del carrito (vista web/móvil del HU 12)
    // GET /api/carrito/items -> 200 con los ítems y sus subtotales
    // ------------------------------------------------------------------
    // HU 13 - Eliminar un producto del carrito
    public void eliminarItem(Long idItemCarrito) {

        ItemCarrito item = itemCarritoRepository.findById(idItemCarrito)
                .orElseThrow(() -> new ItemCarritoNoEncontradoException(
                        "Ítem de carrito no encontrado con id " + idItemCarrito));

        itemCarritoRepository.delete(item);
    }

    public List<ItemCarritoResponse> listarItems(Long clienteId) {

        Carrito carrito = carritoRepository.findByClienteId(resolverCliente(clienteId))
                .orElse(null);

        if (carrito == null) {
            return List.of();
        }

        return itemCarritoRepository.findByCarritoId(carrito.getId()).stream()
                .map(this::toResponse)
                .toList();
    }


    // HU 14 - Calcular el total del carrito
    public BigDecimal calcularTotal(Long clienteId) {
        return listarItems(clienteId).stream()
                .map(ItemCarritoResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    // Reglas privadas
    // ------------------------------------------------------------------
    private void validarCantidadContraStock(int cantidad, int stockDisponible) {

        if (cantidad < 1) {
            throw new ItemCarritoInvalidoException("La cantidad debe ser al menos 1");
        }

        if (cantidad > stockDisponible) {
            throw new ItemCarritoInvalidoException(
                    "La cantidad solicitada (" + cantidad + ") supera el stock disponible ("
                            + stockDisponible + ")");
        }
    }

    private Carrito buscarOCrearCarrito(Long clienteId) {

        Long cliente = resolverCliente(clienteId);

        return carritoRepository.findByClienteId(cliente)
                .orElseGet(() -> carritoRepository.save(new Carrito(cliente)));
    }

    private Long resolverCliente(Long clienteId) {
        return (clienteId == null) ? CLIENTE_DEFECTO : clienteId;
    }

    /**
     * Subtotal calculado SIEMPRE en el servidor: cantidad * precioUnitario.
     */
    private ItemCarritoResponse toResponse(ItemCarrito item) {

        BigDecimal subtotal = item.getPrecioUnitario()
                .multiply(BigDecimal.valueOf(item.getCantidad()));

        return new ItemCarritoResponse(
                item.getId(),
                item.getProducto().getId(),
                item.getProducto().getNombre(),
                item.getCantidad(),
                item.getPrecioUnitario(),
                subtotal,
                item.getProducto().getStock());
    }
}
