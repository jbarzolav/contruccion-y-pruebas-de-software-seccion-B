package com.ejemplo.backenduserapi.dto;

import com.ejemplo.backenduserapi.entity.PerfilVendedor;

/**
 * HU 29 - Respuesta segura del perfil comercial del vendedor autenticado.
 * No expone contraseña, hash, token ni datos administrativos.
 */
public record PerfilVendedorResponse(Long usuarioId, String nombreComercial, String descripcion,
                                     String telefonoContacto) {

    public static PerfilVendedorResponse de(PerfilVendedor p) {
        return new PerfilVendedorResponse(p.getUsuarioId(), p.getNombreComercial(), p.getDescripcion(),
                p.getTelefonoContacto());
    }
}
