package com.ejemplo.backenduserapi.entity;

import jakarta.persistence.*;

/**
 * HU 21 - Cuenta de usuario para el inicio de sesión seguro.
 *
 * La contraseña se almacena únicamente como hash BCrypt
 * (passwordHash): jamás se guarda ni se devuelve en texto plano.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correo único e irremplazable: identifica a la cuenta. */
    @Column(nullable = false, unique = true)
    private String correo;

    /** Hash BCrypt de la contraseña (nunca la contraseña en claro). */
    @Column(nullable = false)
    private String passwordHash;

    public Usuario() {
    }

    public Usuario(String correo, String passwordHash) {
        this.correo = correo;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
