package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** HU 41 - Una calificación por compra realizada. */
@Entity
@Table(name = "calificaciones", uniqueConstraints = {
        @UniqueConstraint(name = "uk_calificacion_pedido", columnNames = "pedido_id")
})
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "vendedor_id", nullable = false)
    private Long vendedorId;

    @Column(nullable = false)
    private Integer valor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public Calificacion() {
    }

    public Calificacion(Pedido pedido, Long clienteId, Long vendedorId, Integer valor, LocalDateTime fechaCreacion) {
        this.pedido = pedido;
        this.clienteId = clienteId;
        this.vendedorId = vendedorId;
        this.valor = valor;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public Long getClienteId() { return clienteId; }
    public Long getVendedorId() { return vendedorId; }
    public Integer getValor() { return valor; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
