package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Credenciales OAuth del vendedor: únicamente ciphertext AES-GCM en la base de datos. */
@Entity
@Table(name = "mp_vendedores")
public class MercadoPagoVendedor {
    @Id
    private Long vendedorId;
    @Column(nullable = false, unique = true)
    private Long mercadoPagoUserId;
    @Column(nullable = false, length = 2048)
    private String accessCifrado;
    @Column(nullable = false, length = 2048)
    private String refreshCifrado;
    @Column(nullable = false)
    private Instant vence;

    protected MercadoPagoVendedor() {}

    public MercadoPagoVendedor(Long vendedorId) { this.vendedorId = vendedorId; }

    public Long getVendedorId() { return vendedorId; }
    public Long getMercadoPagoUserId() { return mercadoPagoUserId; }
    public String getAccessCifrado() { return accessCifrado; }
    public String getRefreshCifrado() { return refreshCifrado; }
    public Instant getVence() { return vence; }

    public void actualizar(Long userId, String accessCifrado, String refreshCifrado, Instant vence) {
        this.mercadoPagoUserId = userId;
        this.accessCifrado = accessCifrado;
        this.refreshCifrado = refreshCifrado;
        this.vence = vence;
    }
}
