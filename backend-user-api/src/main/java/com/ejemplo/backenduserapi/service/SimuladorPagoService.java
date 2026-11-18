package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.entity.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Operaciones de la pasarela local; nunca contacta a un procesador financiero. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class SimuladorPagoService {
    @PersistenceContext private EntityManager em;
    private final CheckoutService checkout;
    private final AlertaStockService alertas;
    private final NotificacionService avisos;
    public SimuladorPagoService(CheckoutService checkout, AlertaStockService alertas, NotificacionService avisos) {
        this.checkout=checkout; this.alertas=alertas; this.avisos=avisos;
    }
    public record Aviso(String referencia, BigDecimal importe, String moneda, String resultado) {}
    static void validar(Pedido p, Aviso a) {
        if (!Objects.equals(p.getReferenciaPago(),a.referencia()) || a.importe()==null || p.getTotal().compareTo(a.importe())!=0 || !"PEN".equals(a.moneda()))
            throw new ResponseStatusException(BAD_REQUEST,"Referencia, importe o moneda inconsistentes");
    }
    public CheckoutService.Vista resolver(Long id, Usuario u, Aviso a) {
        Pedido p=checkout.propio(id,u); validar(p,a);
        if (!"APROBADO".equals(a.resultado()) && !"RECHAZADO".equals(a.resultado())) throw new ResponseStatusException(BAD_REQUEST,"Resultado inválido");
        String destino="APROBADO".equals(a.resultado())?"PAGADO":"RECHAZADO";
        if (destino.equals(p.getEstadoPago())) return CheckoutService.vista(p);
        if (!"PENDIENTE".equals(p.getEstadoPago()) || !p.getReservaHasta().isAfter(LocalDateTime.now())) throw new ResponseStatusException(CONFLICT,"Intento cerrado o reserva vencida");
        if ("PAGADO".equals(destino)) {
            for (var d:p.getDetalles().stream().sorted(Comparator.comparing(x->x.getProducto().getId())).toList()) {
                ItemCarrito i=em.find(ItemCarrito.class,d.getItemOrigenId(),LockModeType.PESSIMISTIC_WRITE);
                if (i==null || !i.getCarrito().getClienteId().equals(u.getId()) || i.getCantidad()<d.getCantidad()) throw new ResponseStatusException(CONFLICT,"La selección del carrito cambió");
                Producto pr=d.getProducto(); em.refresh(pr,LockModeType.PESSIMISTIC_WRITE);
                int antes=pr.getStock(); if (antes<d.getCantidad()) throw new ResponseStatusException(CONFLICT,"Stock insuficiente");
                pr.setStock(antes-d.getCantidad()); alertas.registrarCambioStock(pr,antes,pr.getStock());
                if (i.getCantidad().equals(d.getCantidad())) em.remove(i); else i.setCantidad(i.getCantidad()-d.getCantidad());
            }
            p.setEstado("EN_PROCESO"); p.setFechaActualizacionEstado(LocalDateTime.now()); avisos.notificarNuevaCompra(p);
        }
        p.setEstadoPago(destino); return CheckoutService.vista(p);
    }
}
