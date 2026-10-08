package com.ejemplo.backenduserapi.dto;

/**
 * Consulta enviada al chatbot (HU 16).
 * Se valida en el servicio: la consulta no puede ser nula ni vacía.
 */
public class ChatbotRequest {

    private String consulta;

    public ChatbotRequest() {
    }

    public ChatbotRequest(String consulta) {
        this.consulta = consulta;
    }

    public String getConsulta() {
        return consulta;
    }

    public void setConsulta(String consulta) {
        this.consulta = consulta;
    }
}
