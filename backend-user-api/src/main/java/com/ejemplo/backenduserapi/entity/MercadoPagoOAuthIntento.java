package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Estado OAuth de un solo uso; nunca se almacena el state enviado al navegador en claro. */
@Entity
@Table(name = "mp_oauth_intentos")
public class MercadoPagoOAuthIntento {
    @Id
    @Column(length = 64)
    private String stateHash;
    @Column(nullable = false)
    private Long vendedorId;
    @Column(nullable = false)
    private Instant vence;
    @Column(length = 500)
    private String verifierCifrado;

    protected MercadoPagoOAuthIntento() {}

    public MercadoPagoOAuthIntento(String stateHash, Long vendedorId, Instant vence, String verifierCifrado) {
        this.stateHash = stateHash;
        this.vendedorId = vendedorId;
        this.vence = vence;
        this.verifierCifrado = verifierCifrado;
    }

    public Long getVendedorId() { return vendedorId; }
    public Instant getVence() { return vence; }
    public String getVerifierCifrado() { return verifierCifrado; }
}
