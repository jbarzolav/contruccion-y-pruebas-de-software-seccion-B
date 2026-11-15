package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * HU 59.2 - Identificador de intento de compra (idempotencia).
 *
 * El cliente envia una clave unica por compra (cabecera "Idempotency-Key").
 * Si la confirmacion se pierde en la red y el cliente reenvia la misma
 * clave, se recupera el MISMO pedido sin repetir el descuento de stock
 * ni los avisos a los vendedores.
 *
 * La clave es unica en toda la tabla: solo puede apuntar a un pedido.
 */
@Entity
@Table(name = "intentos_compra", uniqueConstraints = @UniqueConstraint(columnNames = "clave"))
public class IntentoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clave unica generada por el cliente para una compra concreta. */
    @Column(nullable = false, unique = true, length = 100)
    private String clave;

    /** Comprador que hizo la solicitud (la clave no sirve para otra cuenta). */
    @Column(nullable = false)
    private Long clienteId;

    /** Pedido registrado con esta clave: es el que se devuelve al reenviar. */
    @Column(nullable = false)
    private Long idPedido;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public IntentoCompra() {
    }

    public IntentoCompra(String clave, Long clienteId, Long idPedido, LocalDateTime fecha) {
        this.clave = clave;
        this.clienteId = clienteId;
        this.idPedido = idPedido;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public String getClave() {
        return clave;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public Long getIdPedido() {
        return idPedido;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
