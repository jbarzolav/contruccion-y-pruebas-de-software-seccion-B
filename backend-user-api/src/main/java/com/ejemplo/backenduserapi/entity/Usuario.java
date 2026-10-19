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

    // HU 28 - Datos personales editables por el propio usuario (nullables:
    // las cuentas creadas antes de esta historia siguen siendo válidas).
    @Column(length = 100)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    // HU 30 - Foto del perfil guardada junto a los archivos del backend.
    @Column(length = 500)
    private String fotoUrl;

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    // HU 23 - Una cuenta comienza sin privilegios administrativos.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol = Rol.CLIENTE;
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }
}
