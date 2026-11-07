package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * HU 58 - Umbral de stock configurado por el vendedor para su publicación.
 *
 * Se guarda en una tabla propia (y no como columnas de Producto) para no
 * alterar el JSON público del catálogo ni el contrato de las publicaciones.
 *
 * `alertaDisparada` indica que el episodio de stock bajo actual ya fue
 * notificado: mientras siga en true no se repiten avisos. Se rearma cuando el
 * stock vuelve a estar por encima del umbral (reposición).
 *
 * `alertasEmitidas` es un contador por producto que permite generar una clave
 * de evento distinta en cada cruzada, de modo que un aviso repetido tras una
 * reposición no colisione con el anterior.
 */
@Entity
@Table(name = "umbrales_stock", uniqueConstraints =
        @UniqueConstraint(name = "uk_umbral_stock_producto", columnNames = "producto_id"))
public class UmbralStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Umbral configurado: entero mayor o igual a cero. */
    @Column(nullable = false)
    private Integer umbral;

    /** True cuando el episodio de stock bajo actual ya fue notificado. */
    @Column(nullable = false)
    private boolean alertaDisparada = false;

    /** Cantidad de avisos emitidos para este producto (clave de evento). */
    @Column(nullable = false)
    private Integer alertasEmitidas = 0;

    @Column(nullable = false)
    private LocalDateTime fechaActualizacion;

    public UmbralStock() {
    }

    public UmbralStock(Producto producto, Integer umbral, LocalDateTime fechaActualizacion) {
        this.producto = producto;
        this.umbral = umbral;
        this.fechaActualizacion = fechaActualizacion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public Integer getUmbral() {
        return umbral;
    }

    public void setUmbral(Integer umbral) {
        this.umbral = umbral;
    }

    public boolean isAlertaDisparada() {
        return alertaDisparada;
    }

    public void setAlertaDisparada(boolean alertaDisparada) {
        this.alertaDisparada = alertaDisparada;
    }

    public Integer getAlertasEmitidas() {
        return alertasEmitidas;
    }

    public void setAlertasEmitidas(Integer alertasEmitidas) {
        this.alertasEmitidas = alertasEmitidas;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
