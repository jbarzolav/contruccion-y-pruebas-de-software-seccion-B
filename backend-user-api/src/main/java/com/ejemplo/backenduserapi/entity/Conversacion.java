package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "conversaciones", uniqueConstraints = @UniqueConstraint(
        name = "uk_conversacion_participantes_producto", columnNames = {"producto_id", "cliente_id", "vendedor_id"}))
public class Conversacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vendedor_id", nullable = false)
    private Usuario vendedor;
    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    protected Conversacion() { }
    public Conversacion(Producto producto, Usuario cliente, Usuario vendedor) {
        this.producto = producto;
        this.cliente = cliente;
        this.vendedor = vendedor;
        this.fechaCreacion = Instant.now();
    }
    public Long getId() { return id; }
    public Producto getProducto() { return producto; }
    public Usuario getCliente() { return cliente; }
    public Usuario getVendedor() { return vendedor; }
    public Instant getFechaCreacion() { return fechaCreacion; }
}
