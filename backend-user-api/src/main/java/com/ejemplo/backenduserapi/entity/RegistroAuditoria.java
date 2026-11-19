package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "registros_auditoria")
public class RegistroAuditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private AccionAuditoria accion;

    @Column(nullable = false, length = 255)
    private String usuario;

    @Column(name = "usuario_afectado_id")
    private Long usuarioAfectadoId;

    @Column(nullable = false)
    private Instant fecha;

    protected RegistroAuditoria() { }

    public RegistroAuditoria(AccionAuditoria accion, String usuario, Instant fecha) {
        this(accion, usuario, fecha, null);
    }

    public RegistroAuditoria(AccionAuditoria accion, String usuario, Instant fecha, Long usuarioAfectadoId) {
        this.accion = accion;
        this.usuario = usuario;
        this.fecha = fecha;
        this.usuarioAfectadoId = usuarioAfectadoId;
    }

    public Long getId() { return id; }
    public AccionAuditoria getAccion() { return accion; }
    public String getUsuario() { return usuario; }
    public Long getUsuarioAfectadoId() { return usuarioAfectadoId; }
    public Instant getFecha() { return fecha; }
}
