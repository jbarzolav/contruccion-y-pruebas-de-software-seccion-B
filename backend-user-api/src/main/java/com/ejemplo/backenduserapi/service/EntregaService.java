package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** El propio id de pedido es el id del hilo: abrirlo no crea otro recurso. */
@Service
@Transactional
public class EntregaService {
    @PersistenceContext protected EntityManager em;
    public record Hilo(Long idPedido, String codigoPedido, Long compradorId, Long vendedorId) {}
    protected Pedido participante(Long id, Usuario u) {
        Pedido p=em.find(Pedido.class,id);
        if(p==null) throw new ResponseStatusException(NOT_FOUND,"Pedido inexistente");
        boolean comprador=p.getClienteId().equals(u.getId()) && u.getRol()==Rol.CLIENTE;
        boolean vendedor=u.getRol()==Rol.VENDEDOR && (p.getVendedorId()!=null ? p.getVendedorId().equals(u.getId()) :
                p.getDetalles().stream().anyMatch(d->u.getId().equals(d.getProducto().getVendedorId())));
        if (!comprador && !vendedor) throw new ResponseStatusException(FORBIDDEN,"No participas en este pedido");
        return p;
    }
    public CheckoutService.Vista resumen(Long id, Usuario u) {
        Pedido p=participante(id,u);
        if (p.getCodigoPedido()==null) throw new ResponseStatusException(NOT_FOUND,"Pedido histórico sin checkout");
        return CheckoutService.vista(p);
    }
    public Hilo abrir(Long id, Usuario u) {
        var p=participante(id,u);
        if (!"PAGADO".equals(p.getEstadoPago())) throw new ResponseStatusException(CONFLICT,"Solo los pedidos pagados tienen chat de entrega");
        return new Hilo(id,p.getCodigoPedido(),p.getClienteId(),p.getVendedorId());
    }
}
