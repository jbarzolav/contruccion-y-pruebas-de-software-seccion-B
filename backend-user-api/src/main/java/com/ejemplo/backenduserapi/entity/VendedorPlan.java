package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

import java.sql.Timestamp;

/**
 * HU 19 - Plan Premium de un vendedor, persistido en la BD (nada simulado):
 * el backend verifica aquí que el plan se encuentre activo antes de
 * permitir destacar publicaciones.
 */
@Entity
@Table(name = "vendedores_plan")
public class VendedorPlan {

    /** El vendedor al que pertenece el plan (sin autenticación aún: id fijo). */
    @Id
    private Long vendedorId;

    /** true = plan Premium activo. */
    @Column(nullable = false)
    private boolean premiumActivo = false;

    /** Momento en que se activó el plan. */
    private Timestamp fechaActivacion;

    /** Momento en que se desactivó el plan. */
    private Timestamp fechaDesactivacion;

    public VendedorPlan() {
    }

    public VendedorPlan(Long vendedorId, boolean premiumActivo) {
        this.vendedorId = vendedorId;
        this.premiumActivo = premiumActivo;
    }

    public Long getVendedorId() {
        return vendedorId;
    }

    public void setVendedorId(Long vendedorId) {
        this.vendedorId = vendedorId;
    }

    public boolean isPremiumActivo() {
        return premiumActivo;
    }

    public void setPremiumActivo(boolean premiumActivo) {
        this.premiumActivo = premiumActivo;
    }

    public Timestamp getFechaActivacion() {
        return fechaActivacion;
    }

    public void setFechaActivacion(Timestamp fechaActivacion) {
        this.fechaActivacion = fechaActivacion;
    }

    public Timestamp getFechaDesactivacion() {
        return fechaDesactivacion;
    }

    public void setFechaDesactivacion(Timestamp fechaDesactivacion) {
        this.fechaDesactivacion = fechaDesactivacion;
    }
}
