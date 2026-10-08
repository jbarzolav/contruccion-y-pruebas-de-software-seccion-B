package com.ejemplo.backenduserapi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Cliente HTTP hacia Google AI Studio — Gemini 2.0 Flash (HU 16).
 *
 * POST {gemini.api.url} con header x-goog-api-key y body:
 *   - systemInstruction: catálogo de productos + reglas de respuesta
 *   - contents: la consulta del usuario
 *   - generationConfig.responseMimeType/responseSchema: el modelo DEBE
 *     responder JSON estructurado {"mensaje": "...", "productosIds": [...]}
 *
 * Política de fallo: CUALQUIER problema (clave vacía, timeout, sin internet,
 * HTTP distinto de 2xx, cuerpo ilegible) devuelve Optional.empty() para que
 * ChatbotService active su FALLBACK local por palabras clave en la BD.
 * Nunca lanza excepciones hacia arriba: la suite de pruebas corre sin red.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(3);
    private static final Duration TIMEOUT_RESPUESTA = Duration.ofSeconds(8);

    private final String apiKey;
    private final String apiUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(@Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.api.url:}") String apiUrl) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT_CONEXION)
                .build();
    }

    /**
     * Genera la respuesta del modelo para la consulta.
     *
     * @param systemPrompt instrucciones + catálogo de productos disponibles
     * @param consulta     pregunta del usuario
     * @return texto JSON del modelo, o vacío si Gemini no está disponible
     */
    public Optional<String> generarContenidoJson(String systemPrompt, String consulta) {

        if (apiKey == null || apiKey.isBlank() || apiUrl == null || apiUrl.isBlank()) {
            log.warn("Gemini sin clave o sin URL configurada: se usará el respaldo local.");
            return Optional.empty();
        }

        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(TIMEOUT_RESPUESTA)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            construirCuerpo(systemPrompt, consulta), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> respuesta = httpClient.send(
                    peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (respuesta.statusCode() / 100 != 2) {
                log.warn("Gemini respondió HTTP {}: se usará el respaldo local.",
                        respuesta.statusCode());
                return Optional.empty();
            }

            return extraerTextoDeCandidatos(respuesta.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Llamada a Gemini interrumpida: se usará el respaldo local.");
            return Optional.empty();
        } catch (Exception e) {
            // timeout, sin conexión a internet, URL inválida, JSON ilegible…
            log.warn("Sin respuesta de Gemini ({}): se usará el respaldo local.",
                    e.getMessage());
            return Optional.empty();
        }
    }

    /** Arma el body JSON exigido por la API v1beta (generateContent). */
    private String construirCuerpo(String systemPrompt, String consulta) throws Exception {
        ObjectNode raiz = mapper.createObjectNode();

        // Instrucciones del sistema (catálogo + formato obligatorio)
        ArrayNode partesSystem = raiz.putObject("systemInstruction").putArray("parts");
        partesSystem.addObject().put("text", systemPrompt);

        // Consulta del usuario
        ObjectNode mensajeUsuario = raiz.putArray("contents").addObject();
        mensajeUsuario.put("role", "user");
        mensajeUsuario.putArray("parts").addObject().put("text", consulta);

        // Respuesta obligatoria en JSON estructurado
        ObjectNode generationConfig = raiz.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        ObjectNode esquema = generationConfig.putObject("responseSchema");
        esquema.put("type", "OBJECT");
        ObjectNode propiedades = esquema.putObject("properties");
        propiedades.putObject("mensaje").put("type", "STRING");
        ObjectNode productosIds = propiedades.putObject("productosIds");
        productosIds.put("type", "ARRAY");
        productosIds.putObject("items").put("type", "INTEGER");
        ArrayNode orden = esquema.putArray("propertyOrdering");
        orden.add("mensaje");
        orden.add("productosIds");

        return mapper.writeValueAsString(raiz);
    }

    /** Lee candidates[0].content.parts[*].text de la respuesta de Gemini. */
    private Optional<String> extraerTextoDeCandidatos(String cuerpo) {
        try {
            JsonNode raiz = mapper.readTree(cuerpo);
            JsonNode partes = raiz.path("candidates").path(0).path("content").path("parts");
            StringBuilder texto = new StringBuilder();
            for (JsonNode parte : partes) {
                texto.append(parte.path("text").asText(""));
            }
            String resultado = texto.toString().trim();
            return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado);
        } catch (Exception e) {
            log.warn("Respuesta de Gemini ilegible: se usará el respaldo local.");
            return Optional.empty();
        }
    }
}
