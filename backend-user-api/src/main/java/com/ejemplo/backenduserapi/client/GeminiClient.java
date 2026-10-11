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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cliente HTTP hacia Google AI Studio (HU 16).
 *
 * POST {base}/models/{modelo}:generateContent con header x-goog-api-key y body:
 *   - systemInstruction: catálogo de productos + reglas de respuesta
 *   - contents: la consulta del usuario
 *   - generationConfig.responseMimeType/responseSchema: el modelo DEBE
 *     responder JSON estructurado {"mensaje": "...", "productosIds": [...]}
 *
 * RESILIENCIA (fallback y retry con rotación de modelos):
 *   Los modelos se prueban en cascada según su orden de prioridad
 *   (property gemini.api.modelos; el modelo embebido en gemini.api.url,
 *   si existe, queda primero para no romper configuraciones previas).
 *   Si un modelo falla — 429 Too Many Requests, timeout, HTTP 4xx/5xx,
 *   sin internet o cuerpo ilegible — se registra el fallo en el log y se
 *   pasa INMEDIATAMENTE al siguiente modelo de la lista.
 *   Si TODOS fallan, se devuelve Optional.empty() para que ChatbotService
 *   active su FALLBACK local por palabras clave en la BD: la API nunca
 *   responde con un error HTTP 500 crudo al usuario final.
 *   Nunca lanza excepciones hacia arriba: la suite de pruebas corre sin red.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(3);
    private static final Duration TIMEOUT_RESPUESTA = Duration.ofSeconds(8);

    /** Cascada por defecto: modelo principal y luego el más liviano. */
    private static final String MODELOS_POR_DEFECTO =
            "gemini-3.5-flash,gemini-1.5-flash";

    private final String apiKey;
    private final String apiUrl;
    /** Modelos en orden de prioridad (inmutable). */
    private final List<String> modelos;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(@Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.api.url:}") String apiUrl,
                        @Value("${gemini.api.modelos:" + MODELOS_POR_DEFECTO + "}")
                        String modelosConfigurados) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.modelos = construirCascada(apiUrl, modelosConfigurados);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT_CONEXION)
                .build();
    }

    /**
     * Genera la respuesta del modelo para la consulta, probando los modelos
     * en cascada: si uno falla se pasa al siguiente de la lista.
     *
     * @param systemPrompt instrucciones + catálogo de productos disponibles
     * @param consulta     pregunta del usuario
     * @return texto JSON del modelo, o vacío si NINGÚN modelo respondió
     */
    public Optional<String> generarContenidoJson(String systemPrompt, String consulta) {

        if (apiKey == null || apiKey.isBlank() || apiUrl == null || apiUrl.isBlank()) {
            log.warn("Gemini sin clave o sin URL configurada: se usará el respaldo local.");
            return Optional.empty();
        }

        for (String modelo : modelos) {
            try {
                Optional<String> respuesta =
                        intentarModelo(modelo, systemPrompt, consulta);

                if (respuesta.isPresent()) {
                    return respuesta;
                }

                // HTTP 2xx pero sin contenido usable: se prueba el siguiente.
                log.warn("Gemini modelo '{}' respondió sin contenido usable: "
                        + "se intenta el siguiente modelo de la cascada.", modelo);

            } catch (InterruptedException e) {
                // Interrumpir el hilo no debe seguir gastando cuota: se corta.
                Thread.currentThread().interrupt();
                log.warn("Llamada a Gemini interrumpida en '{}': "
                        + "se usará el respaldo local.", modelo);
                return Optional.empty();

            } catch (Exception e) {
                // 429 Too Many Requests, timeout, 4xx/5xx, sin internet…
                log.warn("Gemini modelo '{}' falló ({}): "
                        + "se intenta el siguiente modelo de la cascada.",
                        modelo, e.getMessage());
            }
        }

        log.warn("Gemini agotó los {} modelos de la cascada ({}): "
                + "se usará el respaldo local.", modelos.size(), modelos);
        return Optional.empty();
    }

    /** Prueba UN modelo: lanza excepción si la llamada o el cuerpo fallan. */
    private Optional<String> intentarModelo(
            String modelo,
            String systemPrompt,
            String consulta
    ) throws Exception {

        HttpRequest peticion = HttpRequest.newBuilder(URI.create(urlPorModelo(modelo)))
                .timeout(TIMEOUT_RESPUESTA)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        construirCuerpo(systemPrompt, consulta), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> respuesta = httpClient.send(
                peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (respuesta.statusCode() / 100 != 2) {
            // Incluye 429 Too Many Requests: se marca este modelo como fallido.
            throw new IllegalStateException("HTTP " + respuesta.statusCode());
        }

        return extraerTextoDeCandidatos(respuesta.body());
    }

    /**
     * Armа la cascada priorizando el modelo embebido en la URL (si la
     * configuración vigente apunta a un modelo concreto, ese queda primero)
     * y agregando luego los modelos de la property, sin duplicados.
     */
    private static List<String> construirCascada(String url, String modelosConfigurados) {

        List<String> cascada = new ArrayList<>();

        String modeloDeLaUrl = extraerModeloDeLaUrl(url);
        if (modeloDeLaUrl != null) {
            cascada.add(modeloDeLaUrl);
        }

        if (modelosConfigurados != null) {
            for (String candidato : modelosConfigurados.split(",")) {
                String modelo = candidato.trim();
                if (!modelo.isEmpty() && !cascada.contains(modelo)) {
                    cascada.add(modelo);
                }
            }
        }

        return List.copyOf(cascada);
    }

    /** Lee el modelo de una URL .../models/{modelo}:generateContent, si existe. */
    private static String extraerModeloDeLaUrl(String url) {
        if (url == null) {
            return null;
        }
        int posicion = url.indexOf("/models/");
        if (posicion < 0) {
            return null;
        }
        String cola = url.substring(posicion + "/models/".length());
        String modelo = cola.split(":", 2)[0].trim();
        return modelo.isEmpty() ? null : modelo;
    }

    /** URL exacta del generateContent para el modelo de este intento. */
    private String urlPorModelo(String modelo) {
        int posicion = apiUrl.indexOf("/models/");
        String base = posicion >= 0
                ? apiUrl.substring(0, posicion)
                : apiUrl;
        return base.replaceAll("/+$", "")
                + "/models/" + modelo + ":generateContent";
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
