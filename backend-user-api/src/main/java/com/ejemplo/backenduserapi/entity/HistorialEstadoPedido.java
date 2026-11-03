package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * HU 49 - Historial mínimo de cambios de estado de un pedido.
 *
 * Cada cambio de estado deja una fila con el estado anterior, el nuevo,
 * la fecha del cambio y el vendedor que lo realizó. Sirve para auditar
 * el avance de la compra informada al cliente.
 */
@Entity
@Table(name = "historial_estados_pedido")
public class HistorialEstadoPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(nullable = false, length = 30)
    private String estadoAnterior;

    @Column(nullable = false, length = 30)
    private String estadoNuevo;

    @Column(nullable = false)
    private LocalDateTime fechaCambio;

    /** Vendedor que realizó el cambio (auditoría). */
    @Column(nullable = false)
    private Long usuarioId;

    public HistorialEstadoPedido() {
    }

    public HistorialEstadoPedido(Pedido pedido, String estadoAnterior, String estadoNuevo,
                                 LocalDateTime fechaCambio, Long usuarioId) {
        this.pedido = pedido;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.fechaCambio = fechaCambio;
        this.usuarioId = usuarioId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(String estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(String estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public LocalDateTime getFechaCambio() {
        return fechaCambio;
    }

    public void setFechaCambio(LocalDateTime fechaCambio) {
        this.fechaCambio = fechaCambio;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }
}
