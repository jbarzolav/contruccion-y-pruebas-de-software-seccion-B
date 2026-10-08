package com.ejemplo.backenduserapi.dto;

import java.util.List;

/**
 * Respuesta del chatbot (HU 16).
 * mensaje amigable + lista de productos sugeridos (solo disponibles).
 */
public class ChatbotResponse {

    private final String mensaje;
    private final List<ProductoResumenResponse> productos;

    public ChatbotResponse(String mensaje, List<ProductoResumenResponse> productos) {
        this.mensaje = mensaje;
        this.productos = productos;
    }

    public String getMensaje() {
        return mensaje;
    }

    public List<ProductoResumenResponse> getProductos() {
        return productos;
    }
}
