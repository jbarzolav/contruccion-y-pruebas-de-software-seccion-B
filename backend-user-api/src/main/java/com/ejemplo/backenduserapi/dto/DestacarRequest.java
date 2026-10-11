package com.ejemplo.backenduserapi.dto;

/**
 * HU 19 - Cuerpo OPCIONAL de PATCH /api/productos/{idProducto}/destacado:
 *   destacado = true  -> activar el destacado
 *   destacado = false -> desactivar el destacado
 *   sin cuerpo        -> alterna el estado actual
 */
public class DestacarRequest {

    private Boolean destacado;

    public DestacarRequest() {
    }

    public DestacarRequest(Boolean destacado) {
        this.destacado = destacado;
    }

    public Boolean getDestacado() {
        return destacado;
    }

    public void setDestacado(Boolean destacado) {
        this.destacado = destacado;
    }
}
