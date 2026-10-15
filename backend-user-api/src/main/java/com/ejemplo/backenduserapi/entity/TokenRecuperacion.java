package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tokens_recuperacion")
public class TokenRecuperacion {
    @Id
    private String token;
    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false)
    private Instant vencimiento;

    protected TokenRecuperacion() { }
    public TokenRecuperacion(String token, Usuario usuario, Instant vencimiento) {
        this.token = token;
        this.usuario = usuario;
        this.vencimiento = vencimiento;
    }
    public String getToken() { return token; }
    public Usuario getUsuario() { return usuario; }
    public Instant getVencimiento() { return vencimiento; }
}
