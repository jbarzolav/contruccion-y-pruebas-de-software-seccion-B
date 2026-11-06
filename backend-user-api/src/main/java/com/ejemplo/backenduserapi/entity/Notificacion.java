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

    /** HU53 - Nulo cuando el aviso proviene de una conversación en lugar de una compra. */
    @Column
    private Long pedidoId;

    /** HU53 - Nulo en los avisos de pedido; identifica la conversación del aviso de mensaje. */
    @Column
    private Long conversacionId;

    /** HU54 - Momento de la lectura; permanece nulo mientras el aviso sigue pendiente. */
    @Column
    private LocalDateTime fechaLectura;

    @Column(name = "clave_evento", nullable = false, unique = true, length = 100)
    private String claveEvento;

    protected Notificacion() { }

    /** Aviso ligado a una compra (HU51 / HU52). */
    public Notificacion(String tipo, String resumen, LocalDateTime fecha, Long destinatarioId,
                        Long pedidoId, String claveEvento) {
        this(tipo, resumen, fecha, destinatarioId, pedidoId, null, claveEvento);
    }

    /** Aviso ligado a una compra o a una conversación (HU53). */
    public Notificacion(String tipo, String resumen, LocalDateTime fecha, Long destinatarioId,
                        Long pedidoId, Long conversacionId, String claveEvento) {
        this.tipo = tipo;
        this.resumen = resumen;
        this.fecha = fecha;
        this.estado = "PENDIENTE";
        this.destinatarioId = destinatarioId;
        this.pedidoId = pedidoId;
        this.conversacionId = conversacionId;
        this.claveEvento = claveEvento;
    }

    /**
     * HU54 - Marca el aviso como leído guardando la fecha del evento.
     * Es idempotente: una notificación ya leída no vuelve a modificarse ni cambia su fecha.
     *
     * @return true si la notificación estaba pendiente y se actualizó.
     */
    public boolean marcarLeida(LocalDateTime fechaLectura) {
        if (!"PENDIENTE".equals(estado)) return false;
        this.estado = "LEIDA";
        this.fechaLectura = fechaLectura;
        return true;
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getResumen() { return resumen; }
    public LocalDateTime getFecha() { return fecha; }
    public String getEstado() { return estado; }
    public Long getDestinatarioId() { return destinatarioId; }
    public Long getPedidoId() { return pedidoId; }
    public Long getConversacionId() { return conversacionId; }
    public LocalDateTime getFechaLectura() { return fechaLectura; }
    public String getClaveEvento() { return claveEvento; }
}
