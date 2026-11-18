package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido confirmado (HU 15 - "Confirmar mi compra").
 * Se genera al convertir el carrito activo en un pedido registrado.
 * El total SIEMPRE lo calcula el servidor (suma de los subtotales de los detalles).
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sin autenticación real se usa el cliente 1 (misma regla que vendedorId = 1). */
    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    private LocalDateTime fecha;

    // HU 47 - Estado inicial de la compra y fecha de su último cambio.
    @Column(length = 30)
    private String estado;

    private LocalDateTime fechaActualizacionEstado;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // Nulos en los pedidos históricos: nunca inferir un cobro por el estado de entrega.
    @Column(unique = true, length = 36)
    private String codigoPedido;
    private Long vendedorId;
    private String estadoPago;
    private LocalDateTime reservaHasta;
    @Column(unique = true, length = 36)
    private String referenciaPago;
    private String estadoReembolso;
    public String getEstadoReembolso() { return estadoReembolso; }
    public void setEstadoReembolso(String valor) { estadoReembolso=valor; }

    public void iniciarCheckout(Long vendedor) {
        codigoPedido = java.util.UUID.randomUUID().toString();
        vendedorId = vendedor;
        renovarReserva();
    }
    public void renovarReserva() {
        estadoPago = "PENDIENTE";
        referenciaPago = java.util.UUID.randomUUID().toString();
        reservaHasta = LocalDateTime.now().plusMinutes(15);
    }
    public String getCodigoPedido() { return codigoPedido; }
    public Long getVendedorId() { return vendedorId; }
    public String getEstadoPago() { return estadoPago; }
    public void setEstadoPago(String valor) { estadoPago = valor; }
    public String getReferenciaPago() { return referenciaPago; }
    public LocalDateTime getReservaHasta() { return reservaHasta; }

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DetallePedido> detalles = new ArrayList<>();

    public Pedido() {
    }

    public Pedido(Long clienteId, LocalDateTime fecha) {
        this.clienteId = clienteId;
        this.fecha = fecha;
        this.estado = "REGISTRADO";
        this.fechaActualizacionEstado = fecha;
        this.total = BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getEstado() { return estado == null ? "REGISTRADO" : estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFechaActualizacionEstado() {
        return fechaActualizacionEstado == null ? fecha : fechaActualizacionEstado;
    }
    public void setFechaActualizacionEstado(LocalDateTime fechaActualizacionEstado) {
        this.fechaActualizacionEstado = fechaActualizacionEstado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public List<DetallePedido> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<DetallePedido> detalles) {
        this.detalles = detalles;
    }
}
