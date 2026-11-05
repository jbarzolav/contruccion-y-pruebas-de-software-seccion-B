package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones", uniqueConstraints =
        @UniqueConstraint(name = "uk_notificacion_evento", columnNames = "clave_evento"))
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 300)
    private String resumen;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(nullable = false)
    private Long destinatarioId;

    @Column(nullable = false)
    private Long pedidoId;

    @Column(name = "clave_evento", nullable = false, unique = true, length = 100)
    private String claveEvento;

    protected Notificacion() { }

    public Notificacion(String tipo, String resumen, LocalDateTime fecha, Long destinatarioId,
                        Long pedidoId, String claveEvento) {
        this.tipo = tipo;
        this.resumen = resumen;
        this.fecha = fecha;
        this.estado = "PENDIENTE";
        this.destinatarioId = destinatarioId;
        this.pedidoId = pedidoId;
        this.claveEvento = claveEvento;
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getResumen() { return resumen; }
    public LocalDateTime getFecha() { return fecha; }
    public String getEstado() { return estado; }
    public Long getDestinatarioId() { return destinatarioId; }
    public Long getPedidoId() { return pedidoId; }
    public String getClaveEvento() { return claveEvento; }
}
