
package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.client.GeminiClient;
import com.ejemplo.backenduserapi.dto.ChatbotRequest;
import com.ejemplo.backenduserapi.dto.ChatbotResponse;
import com.ejemplo.backenduserapi.dto.ProductoResumenResponse;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.exception.ChatbotInvalidoException;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * HU 16 - Recomendaciones asistidas por chatbot.
 * HU 17 - Consultas técnicas sobre componentes electrónicos.
 */
@Service
public class ChatbotService {

    private static final int MAXIMO_SUGERENCIAS = 5;

    private static final Set<String> PALABRAS_COMUNES = Set.of(
            "busco", "buscar", "un", "una", "el", "la",
            "los", "las", "quiero", "necesito", "comprar",
            "producto", "productos", "por", "favor", "para",
            "me", "puedes", "recomendar", "recomiendame",
            "tienes", "hay", "alguno", "alguna", "que"
    );

    private final ProductoRepository productoRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatbotService(
            ProductoRepository productoRepository,
            GeminiClient geminiClient
    ) {
        this.productoRepository = productoRepository;
        this.geminiClient = geminiClient;
    }

    // ============================================================
    // HU 16 - RECOMENDACIONES DE PRODUCTOS
    // ============================================================

    public ChatbotResponse recomendar(ChatbotRequest request) {

        if (request == null
                || request.getConsulta() == null
                || request.getConsulta().isBlank()) {
            throw new ChatbotInvalidoException(
                    "La consulta no puede estar vacía"
            );
        }

        String texto = request.getConsulta().trim();

        // Solo productos disponibles y con stock positivo.
        List<Producto> disponibles = catalogoDisponible();

        // Primero intenta obtener recomendaciones de Gemini.
        Optional<String> jsonGemini;

        try {
            jsonGemini = geminiClient.generarContenidoJson(
                    construirSystemPrompt(disponibles),
                    texto
            );
        } catch (Exception e) {
            jsonGemini = Optional.empty();
        }

        if (jsonGemini.isPresent()) {
            try {
                JsonNode raiz = mapper.readTree(jsonGemini.get());
                JsonNode mensaje = raiz.get("mensaje");
                JsonNode ids = raiz.get("productosIds");

                if (mensaje != null
                        && mensaje.isTextual()
                        && !mensaje.asText().isBlank()
                        && ids != null
                        && ids.isArray()) {

                    List<ProductoResumenResponse> productos =
                            mapearIdsAProductos(ids, disponibles);

                    // Gemini seleccionó productos válidos.
                    if (!productos.isEmpty()) {
                        return new ChatbotResponse(
                                mensaje.asText().trim(),
                                productos
                        );
                    }

                    // Gemini no seleccionó ningún ID.
                    // Intentamos recuperar coincidencias locales.
                    if (ids.isEmpty()) {
                        ChatbotResponse local =
                                buscarLocal(texto, disponibles);

                        if (!local.getProductos().isEmpty()) {
                            return local;
                        }
                    }

                    // Si Gemini envió IDs no disponibles, se descartan.
                    // No se reemplazan por otros productos.
                    return new ChatbotResponse(
                            mensaje.asText().trim(),
                            productos
                    );
                }

            } catch (Exception e) {
                // JSON inválido: se utiliza búsqueda local.
            }
        }

        // Gemini no responde o devuelve formato inválido.
        return buscarLocal(texto, disponibles);
    }

    // ============================================================
    // HU 17 - CONSULTAS TÉCNICAS
    // ============================================================

    public ChatbotResponse consultarTecnica(ChatbotRequest request) {

        if (request == null
                || request.getConsulta() == null
                || request.getConsulta().isBlank()) {
            throw new ChatbotInvalidoException(
                    "La consulta no puede estar vacía"
            );
        }

        String consulta = request.getConsulta().trim();

        String instrucciones = """
            Eres un asistente técnico educativo de PulgaTec,
            una plataforma para estudiantes de Tecsup.

            Responde en español preguntas relacionadas con:
            - Componentes electrónicos.
            - Arduino, ESP32 y microcontroladores.
            - Sensores, resistencias, LEDs y protoboards.
            - Circuitos electrónicos y herramientas de laboratorio.
            - Uso y características de materiales académicos tecnológicos.

            Explica los conceptos de manera clara, sencilla y educativa.
            Si la pregunta está fuera de estos temas, indica amablemente
            que tu función es resolver consultas técnicas relacionadas
            con electrónica y componentes tecnológicos.

            Devuelve exclusivamente un JSON con este formato:
            {"mensaje": "explicación técnica", "productosIds": []}

            No recomiendes productos del catálogo.
            El arreglo productosIds debe estar vacío.
            """;

        try {
            Optional<String> respuesta =
                    geminiClient.generarContenidoJson(
                            instrucciones,
                            consulta
                    );

            if (respuesta.isPresent()) {
                JsonNode raiz = mapper.readTree(respuesta.get());
                JsonNode mensaje = raiz.path("mensaje");

                if (mensaje.isTextual()
                        && !mensaje.asText().isBlank()) {
                    return new ChatbotResponse(
                            mensaje.asText().trim(),
                            List.of()
                    );
                }
            }

        } catch (Exception e) {
            // Si Gemini falla, se muestra el mensaje alternativo.
        }

        return new ChatbotResponse(
                "En este momento no puedo responder consultas técnicas. "
                        + "Por favor, intenta nuevamente más tarde.",
                List.of()
        );
    }

    // ============================================================
    // CONVERTIR IDS DE GEMINI EN PRODUCTOS DISPONIBLES
    // ============================================================

    private List<ProductoResumenResponse> mapearIdsAProductos(
            JsonNode ids,
            List<Producto> disponibles
    ) {
        if (ids == null || !ids.isArray()) {
            return List.of();
        }

        List<ProductoResumenResponse> resultado = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();

        for (JsonNode nodo : ids) {

            long id = nodo.asLong(-1);

            if (id < 0 || !vistos.add(id)) {
                continue;
            }

            disponibles.stream()
                    .filter(producto ->
                            producto.getId() != null
                                    && producto.getId() == id
                    )
                    .findFirst()
                    .map(this::toResumen)
                    .ifPresent(resultado::add);

            if (resultado.size() >= MAXIMO_SUGERENCIAS) {
                break;
            }
        }

        return resultado;
    }

    // ============================================================
    // PROMPT DE GEMINI CON EL CATÁLOGO REAL
    // ============================================================

    private String construirSystemPrompt(List<Producto> disponibles) {

        StringBuilder catalogo = new StringBuilder();

        for (Producto producto : disponibles) {
            catalogo.append("- id=")
                    .append(producto.getId())
                    .append(" | ")
                    .append(producto.getNombre())
                    .append(" | S/ ")
                    .append(producto.getPrecio())
                    .append(" | stock ")
                    .append(producto.getStock())
                    .append(" | ")
                    .append(
                            producto.getCategoria() == null
                                    ? "sin categoría"
                                    : producto.getCategoria()
                    )
                    .append('\n');
        }

        return "Eres el asistente de compras de PulgaTec. "
                + "Responde siempre en español.\n"
                + "Devuelve EXCLUSIVAMENTE un JSON válido con esta forma exacta:\n"
                + "{\"mensaje\": \"...\", \"productosIds\": []}\n"
                + "- mensaje: texto breve y amigable para el usuario.\n"
                + "- productosIds: ids enteros de los productos que recomiendas; "
                + "usa [] si no hay coincidencias.\n"
                + "IMPORTANTE: solo puedes usar ids de este catálogo "
                + "(productos disponibles con stock mayor que 0) "
                + "y como máximo " + MAXIMO_SUGERENCIAS + " ids.\n"
                + "Catálogo activo:\n"
                + catalogo;
    }

    // ============================================================
    // BÚSQUEDA LOCAL DE PRODUCTOS
    // ============================================================

    private ChatbotResponse buscarLocal(
            String texto,
            List<Producto> disponibles
    ) {

        List<ProductoResumenResponse> sugerencias =
                disponibles.stream()
                        .filter(producto -> coincide(texto, producto))
                        .limit(MAXIMO_SUGERENCIAS)
                        .map(this::toResumen)
                        .toList();

        String mensaje;

        if (sugerencias.isEmpty()) {
            mensaje = "No encontré productos para \"" + texto
                    + "\". Prueba con otro nombre o categoría.";
        } else {
            mensaje = "Encontré " + sugerencias.size()
                    + (sugerencias.size() == 1
                    ? " producto"
                    : " productos")
                    + " para \"" + texto + "\":";
        }

        return new ChatbotResponse(mensaje, sugerencias);
    }

    // ============================================================
    // CATÁLOGO DISPONIBLE
    // ============================================================

    private List<Producto> catalogoDisponible() {

        return productoRepository.findAll()
                .stream()
                .filter(this::estaDisponible)
                .toList();
    }

    private boolean estaDisponible(Producto producto) {

        boolean estadoDisponible =
                "DISPONIBLE".equalsIgnoreCase(producto.getEstado());

        boolean conStock =
                producto.getStock() != null
                        && producto.getStock() > 0;

        return estadoDisponible && conStock;
    }

    // ============================================================
    // COINCIDENCIA DE NOMBRE O CATEGORÍA
    // ============================================================

    private boolean coincide(String texto, Producto producto) {

        String consulta = texto.toLowerCase(Locale.ROOT).trim();

        String nombre = producto.getNombre() == null
                ? ""
                : producto.getNombre().toLowerCase(Locale.ROOT);

        String categoria = producto.getCategoria() == null
                ? ""
                : producto.getCategoria().toLowerCase(Locale.ROOT);

        // Coincidencia directa.
        if (nombre.contains(consulta) || categoria.contains(consulta)) {
            return true;
        }

        // Ejemplo: "Busco un Arduino Uno" contiene "Arduino Uno".
        if (!nombre.isBlank() && consulta.contains(nombre)) {
            return true;
        }

        // Buscar por palabras importantes de la consulta.
        for (String palabra : consulta.split("[^\\p{L}\\p{N}]+")) {

            if (palabra.length() < 3
                    || PALABRAS_COMUNES.contains(palabra)) {
                continue;
            }

            if (nombre.contains(palabra)
                    || categoria.contains(palabra)) {
                return true;
            }
        }

        return false;
    }

    // ============================================================
    // CONVERTIR PRODUCTO A DTO
    // ============================================================

    private ProductoResumenResponse toResumen(Producto producto) {

        return new ProductoResumenResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getPrecio(),
                producto.getStock(),
                producto.getCategoria(),
                producto.getImagenUrl()
        );
    }
}
