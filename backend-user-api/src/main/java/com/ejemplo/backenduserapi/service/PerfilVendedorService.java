package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.PerfilVendedorRequest;
import com.ejemplo.backenduserapi.dto.PerfilVendedorResponse;
import com.ejemplo.backenduserapi.entity.PerfilVendedor;
import com.ejemplo.backenduserapi.entity.Rol;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.exception.AutorizacionException;
import com.ejemplo.backenduserapi.exception.PerfilInvalidoException;
import com.ejemplo.backenduserapi.repository.PerfilVendedorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 29 - Información comercial del vendedor autenticado.
 *
 * Solo el rol VENDEDOR puede gestionar su perfil comercial; el propietario
 * proviene de la sesión (nunca de un id del body) y la persistencia es
 * idempotente gracias a la FK única usuario_id: si ya existe perfil se
 * actualiza, si no se crea.
 */
@Service
public class PerfilVendedorService {

    private final PerfilVendedorRepository perfiles;

    public PerfilVendedorService(PerfilVendedorRepository perfiles) {
        this.perfiles = perfiles;
    }

    /** HU 29 - GET: devuelve el perfil comercial o uno vacío si aún no se completó. */
    @Transactional(readOnly = true)
    public PerfilVendedorResponse obtener(Usuario usuarioSesion) {
        exigirVendedor(usuarioSesion);
        return perfiles.findByUsuarioId(usuarioSesion.getId())
                .map(PerfilVendedorResponse::de)
                .orElseGet(() -> new PerfilVendedorResponse(usuarioSesion.getId(), "", "", ""));
    }

    /** HU 29 - PATCH: crea o actualiza el perfil del vendedor de la sesión. */
    @Transactional
    public PerfilVendedorResponse guardar(Usuario usuarioSesion, PerfilVendedorRequest solicitud) {
        exigirVendedor(usuarioSesion);

        String nombreComercial = solicitud.getNombreComercial() == null ? "" : solicitud.getNombreComercial().trim();
        String descripcion = solicitud.getDescripcion() == null ? "" : solicitud.getDescripcion().trim();
        String telefono = solicitud.getTelefonoContacto() == null ? "" : solicitud.getTelefonoContacto().trim();

        if (nombreComercial.isEmpty()) {
            throw new PerfilInvalidoException("El nombre comercial es obligatorio.");
        }
        if (descripcion.isEmpty()) {
            throw new PerfilInvalidoException("La descripción es obligatoria.");
        }
        if (telefono.isEmpty()) {
            throw new PerfilInvalidoException("El teléfono de contacto es obligatorio.");
        }
        if (!telefono.matches(PerfilService.PATRON_TELEFONO)) {
            throw new PerfilInvalidoException("El teléfono de contacto debe contener entre 7 y 15 dígitos (se permite +, espacios y guiones).");
        }

        // Idempotente: un solo perfil por vendedor (FK única); actualizar o crear.
        PerfilVendedor perfil = perfiles.findByUsuarioId(usuarioSesion.getId())
                .orElseGet(() -> new PerfilVendedor(usuarioSesion.getId(), "", "", ""));

        perfil.setNombreComercial(nombreComercial);
        perfil.setDescripcion(descripcion);
        perfil.setTelefonoContacto(telefono);

        return PerfilVendedorResponse.de(perfiles.save(perfil));
    }

    /** HU 29 - CLIENTE y ADMINISTRADOR no gestionan perfil de vendedor. */
    private void exigirVendedor(Usuario usuario) {
        if (usuario.getRol() != Rol.VENDEDOR) {
            throw new AutorizacionException(HttpStatus.FORBIDDEN,
                    "Solo los vendedores pueden gestionar información comercial.");
        }
    }
}
