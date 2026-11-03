package com.ejemplo.backenduserapi.dto;

/**
 * HU 49 - Cuerpo de PATCH /api/pedidos/{idPedido}/estado.
 * Solo se acepta el estado destino; el servidor valida la transición.
 */
public class EstadoPedidoRequest {

    private String estado;

    public EstadoPedidoRequest() {
    }

    public EstadoPedidoRequest(String estado) {
        this.estado = estado;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
