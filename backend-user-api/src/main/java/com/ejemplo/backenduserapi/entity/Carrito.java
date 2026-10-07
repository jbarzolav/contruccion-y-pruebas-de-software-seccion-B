package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "carritos")
public class Carrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Cliente dueño del carrito. Sin autenticación real se usa el cliente 1
     * (misma regla que vendedorId = 1 hasta que exista JWT).
     */
    @Column(nullable = false)
    private Long clienteId;

    public Carrito() {
    }

    public Carrito(Long clienteId) {
        this.clienteId = clienteId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }
}
