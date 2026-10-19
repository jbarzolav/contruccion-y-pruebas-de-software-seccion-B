package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

/**
 * HU 29 - Información comercial del vendedor para generar confianza.
 *
 * Entidad 1:1 con Usuario mediante una FK única (usuario_id): un vendedor
 * nunca tiene dos perfiles y la persistencia es idempotente (crear o
 * actualizar el mismo registro). La identidad del propietario proviene de la
 * sesión; el cliente jamás envía usuario_id. Siguiendo el estilo del
 * proyecto, la FK es una columna escalar sin relación JPA declarada.
 */
@Entity
@Table(name = "perfiles_vendedor", uniqueConstraints = {
        @UniqueConstraint(name = "uk_perfil_vendedor_usuario", columnNames = "usuario_id")
})
public class PerfilVendedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Propietario (VENDEDOR) de este perfil; clave única -> sin duplicados. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Nombre comercial público (obligatorio, máx. 80). */
    @Column(nullable = false, length = 80)
    private String nombreComercial;

    /** Presentación del vendedor y lo que ofrece (obligatoria, máx. 500). */
    @Column(nullable = false, length = 500)
    private String descripcion;

    /** Teléfono de contacto público (obligatorio, máx. 20). */
    @Column(name = "telefono_contacto", nullable = false, length = 20)
    private String telefonoContacto;

    public PerfilVendedor() {
    }

    public PerfilVendedor(Long usuarioId, String nombreComercial, String descripcion, String telefonoContacto) {
        this.usuarioId = usuarioId;
        this.nombreComercial = nombreComercial;
        this.descripcion = descripcion;
        this.telefonoContacto = telefonoContacto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public void setTelefonoContacto(String telefonoContacto) {
        this.telefonoContacto = telefonoContacto;
    }
}
