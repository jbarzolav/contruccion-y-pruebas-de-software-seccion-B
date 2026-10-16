package com.ejemplo.backenduserapi.entity;
import java.util.Set;
/** HU 23 - Permisos por rol; Premium sigue siendo un beneficio del vendedor. */
public enum Rol {
    CLIENTE(Set.of("COMPRAR")), VENDEDOR(Set.of("GESTIONAR_PRODUCTOS")), ADMINISTRADOR(Set.of("ASIGNAR_ROLES"));
    private final Set<String> permisos;
    Rol(Set<String> permisos) { this.permisos = permisos; }
    public Set<String> getPermisos() { return permisos; }
}
