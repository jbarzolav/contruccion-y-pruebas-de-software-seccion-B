
package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.client.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChatbotConsultasTecnicasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiClient geminiClient;

    // HU 17 - Consulta técnica válida.
    @Test
    void consultaTecnicaValidaDevuelveExplicacion() throws Exception {
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.of(
                        "{\"mensaje\":\"Una protoboard permite construir circuitos sin soldar.\","
                                + "\"productosIds\":[]}"
                ));

        mockMvc.perform(post("/api/chatbot/consultas-tecnicas")
                        .contentType("application/json")
                        .content("{\"consulta\":\"¿Para qué sirve una protoboard?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(
                        "Una protoboard permite construir circuitos sin soldar."))
                .andExpect(jsonPath("$.productos.length()").value(0));
    }

    // HU 17 - No permitir consultas vacías.
    @Test
    void consultaTecnicaVaciaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/chatbot/consultas-tecnicas")
                        .contentType("application/json")
                        .content("{\"consulta\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(
                        "La consulta no puede estar vacía"));
    }

    // HU 17 - Respuesta alternativa cuando Gemini no está disponible.
    @Test
    void geminiSinRespuestaDevuelveMensajeAlternativo() throws Exception {
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/chatbot/consultas-tecnicas")
                        .contentType("application/json")
                        .content("{\"consulta\":\"¿Qué es un ESP32?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.productos.length()").value(0));
    }

    // HU 17 - JSON inválido no debe provocar error 500.
    @Test
    void geminiJsonInvalidoDevuelveMensajeAlternativo() throws Exception {
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.of("respuesta inválida"));

        mockMvc.perform(post("/api/chatbot/consultas-tecnicas")
                        .contentType("application/json")
                        .content("{\"consulta\":\"¿Qué es una resistencia?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.productos.length()").value(0));
    }

    // HU 17 - El cliente lanza excepción (timeout / sin internet)
    // → mensaje alternativo, jamás error 500.
    @Test
    void geminiLanzaExcepcionDevuelveMensajeAlternativo() throws Exception {
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenThrow(new RuntimeException("timeout simulado (sin internet)"));

        mockMvc.perform(post("/api/chatbot/consultas-tecnicas")
                        .contentType("application/json")
                        .content("{\"consulta\":\"¿Qué es un sensor ultrasónico?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(
                        "En este momento no puedo responder consultas técnicas. "
                                + "Por favor, intenta nuevamente más tarde."))
                .andExpect(jsonPath("$.productos.length()").value(0));
    }
}
