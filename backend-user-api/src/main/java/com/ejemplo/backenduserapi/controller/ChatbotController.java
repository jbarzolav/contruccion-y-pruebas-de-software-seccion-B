package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ChatbotRequest;
import com.ejemplo.backenduserapi.dto.ChatbotResponse;
import com.ejemplo.backenduserapi.service.ChatbotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HU 16 - Chatbot de recomendaciones.
 * POST /api/chatbot/recomendaciones  Body: { "consulta": "notebook" }
 * 200 con mensaje + productos sugeridos (solo disponibles: sin RETIRADO/INACTIVO/stock 0).
 * 400 si la consulta está vacía.
 */
@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = "*")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/recomendaciones")
    public ResponseEntity<ChatbotResponse> recomendar(@RequestBody ChatbotRequest request) {
        return ResponseEntity.ok(chatbotService.recomendar(request));
    }
    /**
     * HU 17 - Consultas técnicas sobre componentes electrónicos.
     * POST /api/chatbot/consultas-tecnicas
     * Body: { "consulta": "¿Para qué sirve una protoboard?" }
     */
    @PostMapping("/consultas-tecnicas")
    public ResponseEntity<ChatbotResponse> consultarTecnica(
            @RequestBody ChatbotRequest request) {
        return ResponseEntity.ok(chatbotService.consultarTecnica(request));
    }
}
