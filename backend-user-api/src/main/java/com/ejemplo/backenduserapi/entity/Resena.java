package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** HU 42 - Una reseña por compra y destino (PRODUCTO o VENDEDOR). */
@Entity
@Table(name = "resenas", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resena_pedido_destino", columnNames = {"pedido_id", "destino_tipo"})
})
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(name = "autor_id", nullable = false)
    private Long autorId;

    @Column(name = "destino_tipo", nullable = false, length = 20)
    private String destinoTipo;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "vendedor_id")
    private Long vendedorId;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    public Resena() {
    }

    public Resena(Pedido pedido, Long autorId, String destinoTipo, Long productoId,
                  Long vendedorId, String texto, LocalDateTime fechaCreacion) {
        this.pedido = pedido;
        this.autorId = autorId;
        this.destinoTipo = destinoTipo;
        this.productoId = productoId;
        this.vendedorId = vendedorId;
        this.texto = texto;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public Long getAutorId() { return autorId; }
    public String getDestinoTipo() { return destinoTipo; }
    public Long getProductoId() { return productoId; }
    public Long getVendedorId() { return vendedorId; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }
}
