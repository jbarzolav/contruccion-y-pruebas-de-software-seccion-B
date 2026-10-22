package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mensajes", indexes = @Index(name = "idx_mensaje_conversacion", columnList = "conversacion_id,id"))
public class Mensaje {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "emisor_id", nullable = false)
    private Usuario emisor;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;
    @Column(nullable = false, updatable = false)
    private Instant fecha;

    protected Mensaje() { }
    public Mensaje(Conversacion conversacion, Usuario emisor, String contenido) {
        this.conversacion = conversacion;
        this.emisor = emisor;
        this.contenido = contenido;
        this.fecha = Instant.now();
    }
    public Long getId() { return id; }
    public Conversacion getConversacion() { return conversacion; }
    public Usuario getEmisor() { return emisor; }
    public String getContenido() { return contenido; }
    public Instant getFecha() { return fecha; }
}
