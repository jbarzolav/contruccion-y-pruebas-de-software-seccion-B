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
import java.util.Optional;
import java.util.Set;

/**
 * HU 16 - Chatbot de recomendaciones (refactor: Google AI Studio / Gemini 2.0 Flash).
 *
 * Flujo:
 *  1. Valida la consulta (no vacía → si no, 400).
 *  2. Recupera el catálogo ACTIVO de la BD: disponibles y con stock &gt; 0.
 *  3. Intenta Gemini: POST con System Prompt (catálogo + reglas) y la consulta;
 *     exige el JSON estructurado {"mensaje": "...", "productosIds": [...]}.
 *  4. FALLBACK: si Gemini falla, expira, no hay internet o el JSON es inválido,
 *     se ejecuta la búsqueda local por palabras clave sobre la BD (la original).
 *  5. La respuesta SIEMPRE es el mismo DTO ChatbotResponse {mensaje, productos[]}
 *     que ya consumen la web (React) y la app (Compose): interfaces intactas.
 *
 * Reglas de negocio (iguales a las de HU 16, incluso con Gemini):
 *  - Solo se sugieren productos disponibles: nada de RETIRADO, INACTIVO ni stock 0.
 *  - Máximo MAXIMO_SUGERENCIAS (5) sugerencias; los ids ajenos o repetidos se ignoran.
 *  - Sin coincidencias → lista vacía + mensaje (200, nunca 404).
 */
@Service
public class ChatbotService {

    private static final int MAXIMO_SUGERENCIAS = 5;

    private final ProductoRepository productoRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatbotService(ProductoRepository productoRepository, GeminiClient geminiClient) {
        this.productoRepository = productoRepository;
        this.geminiClient = geminiClient;
    }

    public ChatbotResponse recomendar(ChatbotRequest request) {

        if (request == null || request.getConsulta() == null || request.getConsulta().isBlank()) {
            throw new ChatbotInvalidoException("La consulta no puede estar vacía");
        }

        String texto = request.getConsulta().trim();

        // Catálogo activo de la BD (H2 en memoria; MySQL con el perfil "mysql")
        List<Producto> disponibles = catalogoDisponible();

        // 1) Intento con Gemini 2.0 Flash…
        ChatbotResponse respuestaGemini = intentarConGemini(texto, disponibles);
        if (respuestaGemini != null) {
            return respuestaGemini;
        }

        // 2) …y si no está disponible, fallback local por palabras clave (HU 16 original)
        return buscarLocal(texto, disponibles);
    }

    // ------------------------------------------------------------------
    // Ruta con Gemini
    // ------------------------------------------------------------------

    /**
     * @return la respuesta armada con Gemini, o null si hay que usar el
     *         respaldo local (sin conexión, timeout, HTTP de error o JSON inválido).
     */
    private ChatbotResponse intentarConGemini(String texto, List<Producto> disponibles) {
        try {
            Optional<String> json = geminiClient.generarContenidoJson(
                    construirSystemPrompt(disponibles), texto);

            if (json.isEmpty()) {
                return null;
            }

            JsonNode raiz = mapper.readTree(json.get());
            JsonNode mensaje = raiz.get("mensaje");
            if (mensaje == null || mensaje.asText("").isBlank()) {
                return null; // formato no conforme → respaldo local
            }

            List<ProductoResumenResponse> productos =
                    mapearIdsAProductos(raiz.get("productosIds"), disponibles);

            return new ChatbotResponse(mensaje.asText().trim(), productos);

        } catch (Exception e) {
            return null; // JSON inválido u otro fallo → respaldo local
        }
    }

    /**
     * Convierte productosIds del modelo en el DTO de producto que consumen
     * web/móvil. Solo traduce ids que existan en el catálogo ACTIVO de la BD:
     * ids inexistentes, retirados, inactivos o sin stock quedan fuera.
     */
    private List<ProductoResumenResponse> mapearIdsAProductos(JsonNode ids, List<Producto> disponibles) {
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
                    .filter(producto -> producto.getId() != null && producto.getId() == id)
                    .findFirst()
                    .map(this::toResumen)
                    .ifPresent(resultado::add);

            if (resultado.size() >= MAXIMO_SUGERENCIAS) {
                break;
            }
        }
        return resultado;
    }

    /** System Prompt: catálogo activo + formato JSON obligatorio de la respuesta. */
    private String construirSystemPrompt(List<Producto> disponibles) {
        StringBuilder catalogo = new StringBuilder();
        for (Producto producto : disponibles) {
            catalogo.append("- id=").append(producto.getId())
                    .append(" | ").append(producto.getNombre())
                    .append(" | S/ ").append(producto.getPrecio())
                    .append(" | stock ").append(producto.getStock())
                    .append(" | ").append(
                            producto.getCategoria() == null ? "sin categoría" : producto.getCategoria())
                    .append('\n');
        }

        return "Eres el asistente de compras de PulgaTec. Responde siempre en español.\n"
                + "Devuelve EXCLUSIVAMENTE un JSON válido con esta forma exacta:\n"
                + "{\"mensaje\": \"...\", \"productosIds\": []}\n"
                + "- mensaje: texto breve y amigable para el usuario.\n"
                + "- productosIds: ids enteros de los productos que recomiendas; "
                + "usa [] si no hay coincidencias.\n"
                + "IMPORTANTE: solo puedes usar ids de este catálogo (productos disponibles "
                + "con stock mayor que 0) y como máximo " + MAXIMO_SUGERENCIAS + " ids.\n"
                + "Catálogo activo:\n"
                + catalogo;
    }

    // ------------------------------------------------------------------
    // Ruta local (fallback de HU 16, byte a byte igual a la original)
    // ------------------------------------------------------------------

    private ChatbotResponse buscarLocal(String texto, List<Producto> disponibles) {

        List<ProductoResumenResponse> sugerencias = disponibles.stream()
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
                    + (sugerencias.size() == 1 ? " producto" : " productos")
                    + " para \"" + texto + "\":";
        }

        return new ChatbotResponse(mensaje, sugerencias);
    }

    // ------------------------------------------------------------------
    // Comunes
    // ------------------------------------------------------------------

    /** Catálogo ACTIVO: solo productos comprables (BD). */
    private List<Producto> catalogoDisponible() {
        return productoRepository.findAll().stream()
                .filter(this::estaDisponible)
                .toList();
    }

    /** Solo productos comprables: nada retirado, inactivo ni agotado (stock 0). */
    private boolean estaDisponible(Producto producto) {
        boolean sinRetiro = !"RETIRADO".equals(producto.getEstado())
                && !"INACTIVO".equals(producto.getEstado());
        boolean conStock = producto.getStock() != null && producto.getStock() > 0;
        return sinRetiro && conStock;
    }

    private boolean coincide(String texto, Producto producto) {
        String objetivo = texto.toLowerCase();
        boolean porNombre = producto.getNombre() != null
                && producto.getNombre().toLowerCase().contains(objetivo);
        boolean porCategoria = producto.getCategoria() != null
                && producto.getCategoria().toLowerCase().contains(objetivo);
        return porNombre || porCategoria;
    }

    private ProductoResumenResponse toResumen(Producto producto) {
        return new ProductoResumenResponse(
                producto.getId(), producto.getNombre(), producto.getPrecio(),
                producto.getStock(), producto.getCategoria(), producto.getImagenUrl());
    }
}
