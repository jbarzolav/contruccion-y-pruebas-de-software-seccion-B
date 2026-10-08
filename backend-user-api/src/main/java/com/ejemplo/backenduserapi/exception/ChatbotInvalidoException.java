package com.ejemplo.backenduserapi.exception;

/**
 * Consulta del chatbot inválida (HU 16) -> 400.
 * Ej.: consulta nula, vacía o solo espacios.
 */
public class ChatbotInvalidoException extends RuntimeException {

    public ChatbotInvalidoException(String mensaje) {
        super(mensaje);
    }
}
