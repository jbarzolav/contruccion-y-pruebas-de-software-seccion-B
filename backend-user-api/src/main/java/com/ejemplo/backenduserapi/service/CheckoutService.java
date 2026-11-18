package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.*;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CheckoutService {
    @PersistenceContext private EntityManager em;
    private final ProductoRepository productos;
    public CheckoutService(ProductoRepository productos) { this.productos = productos; }

    public record Linea(Long productoId, int cantidad, BigDecimal precioUnitario) {}
    public record Vista(Long idPedido, String codigoPedido, Long clienteId, Long vendedorId, String moneda,
            BigDecimal total, String estadoPago, String estadoEntrega, String referencia, LocalDateTime reservaHasta,
            List<Linea> lineas) {}
    public static Vista vista(Pedido p) {
        return new Vista(p.getId(),p.getCodigoPedido(),p.getClienteId(),p.getVendedorId(),"PEN",p.getTotal(),
                p.getEstadoPago(),p.getEstado(),p.getReferenciaPago(),p.getReservaHasta(),p.getDetalles().stream()
                .map(d->new Linea(d.getProducto().getId(),d.getCantidad(),d.getPrecioUnitario())).toList());
    }
    public Pedido propio(Long id, Usuario u) {
        Pedido p = em.find(Pedido.class,id,LockModeType.PESSIMISTIC_WRITE);
        if (p==null) throw new ResponseStatusException(NOT_FOUND,"Pedido inexistente");
        if (!p.getClienteId().equals(u.getId())) throw new ResponseStatusException(FORBIDDEN,"Compra ajena");
        if (p.getCodigoPedido()==null) throw new ResponseStatusException(CONFLICT,"Pedido sin checkout");
        return p;
    }
    public Vista iniciar(Usuario u, Long vendedor, List<Long> ids) {
        if (ids==null || ids.isEmpty() || ids.contains(null) || new HashSet<>(ids).size()!=ids.size())
            throw new ResponseStatusException(BAD_REQUEST,"Selección de carrito inválida");
        em.find(Usuario.class,u.getId(),LockModeType.PESSIMISTIC_WRITE);
        List<ItemCarrito> items = em.createQuery("select i from ItemCarrito i where i.carrito.clienteId=:cliente and i.id in :ids order by i.producto.id",ItemCarrito.class)
                .setParameter("cliente",u.getId()).setParameter("ids",ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (items.size()!=ids.size() || items.stream().anyMatch(i->!Objects.equals(i.getProducto().getVendedorId(),vendedor)))
            throw new ResponseStatusException(BAD_REQUEST,"Selecciona productos propios del carrito y de un solo vendedor");
        Pedido p = new Pedido(u.getId(),LocalDateTime.now()); p.iniciarCheckout(vendedor);
        for (var i:items) {
            var d=new DetallePedido(p,i.getProducto(),i.getCantidad(),i.getPrecioUnitario());
            d.setItemOrigenId(i.getId()); p.getDetalles().add(d); p.setTotal(p.getTotal().add(d.getSubtotal()));
        }
        validarReserva(p); em.persist(p); return vista(p);
    }
    void validarReserva(Pedido p) {
        for (var d:p.getDetalles().stream().sorted(Comparator.comparing(x->x.getProducto().getId())).toList()) {
            Producto producto=d.getProducto(); em.refresh(producto,LockModeType.PESSIMISTIC_WRITE);
            if (!"DISPONIBLE".equals(producto.getEstado()) || producto.getStock()-productos.reservadas(producto.getId(),LocalDateTime.now(),p.getId())<d.getCantidad())
                throw new ResponseStatusException(CONFLICT,"Stock no disponible para " + producto.getNombre());
        }
    }
    public Vista reintentar(Long id, Usuario u) {
        Pedido p=propio(id,u);
        if ("PENDIENTE".equals(p.getEstadoPago()) && p.getReservaHasta().isAfter(LocalDateTime.now())) return vista(p);
        if (!Set.of("PENDIENTE","RECHAZADO").contains(p.getEstadoPago())) throw new ResponseStatusException(CONFLICT,"No admite reintento");
        validarReserva(p); p.renovarReserva(); return vista(p);
    }
    public Vista consultar(Long id, Usuario u) { return vista(propio(id,u)); }
}
