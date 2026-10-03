package com.ejemplo.backenduserapi.dto;

/**
 * Cuerpo OPCIONAL de PATCH /api/productos/{idProducto}/estado
 * (HU 04 - Retirar publicación).
 *
 * Si no se envía el campo, el servicio asigna "RETIRADO"
 * (baja lógica: no se borra el registro de la base de datos).
 */
public class EstadoProductoRequest {

    private String estado;

    public EstadoProductoRequest() {
    }

    public EstadoProductoRequest(String estado) {
        this.estado = estado;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
