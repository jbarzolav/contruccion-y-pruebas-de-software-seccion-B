package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.client.GeminiClient;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 16 - Chatbot: POST /api/chatbot/recomendaciones
 * consulta válida con sugerencias · mensaje vacío · sin coincidencias ·
 * exclusión de no disponibles (RETIRADO / INACTIVO / stock 0).
 *
 * Refactor Gemini 2.0 Flash: el cliente HTTP se MOCKEA con @MockitoBean
 * (reemplazo actual de @MockBean, deprecado desde Spring Boot 3.4), así la
 * suite corre SIN peticiones reales a la API de Google y en verde offline.
 * Si el mock devuelve vacío / lanza excepción / manda JSON inválido, el
 * servicio debe caer en el fallback local por palabras clave de la BD.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatbotRecomendacionesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    /** Cliente de Gemini simulado: jamás toca la red durante la suite. */
    @MockitoBean
    private GeminiClient geminiClient;

    @BeforeEach
    void preparar() {
        limpiar();
        // Por defecto Gemini "no responde" → se ejercita el fallback local
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void finalizar() {
        limpiar();
    }

    private void limpiar() {
        productoRepository.deleteAll();
    }

    private Producto crearProducto(String nombre, String categoria, String precio,
                                   int stock, String estado) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto para pruebas HU16");
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(stock);
        producto.setCategoria(categoria);
        producto.setEstado(estado);
        producto.setImagenUrl("/imagenes/producto.png");
        producto.setVendedorId(1L);
        return productoRepository.save(producto);
    }

    // ---------------------------------------------------------------
    // 1) Consulta válida con sugerencias -> 200 + tarjeta completa
    // ---------------------------------------------------------------
    @Test
    void consultaValidaDevuelveSugerencias() throws Exception {
        crearProducto("Notebook Lenovo", "Computadoras", "2500.00", 5, "DISPONIBLE");

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"notebook\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.productos.length()").value(1))
                .andExpect(jsonPath("$.productos[0].nombre").value("Notebook Lenovo"))
                .andExpect(jsonPath("$.productos[0].precio").value(2500.00))
                .andExpect(jsonPath("$.productos[0].stock").value(5));
    }

    // ---------------------------------------------------------------
    // 2) Mensaje vacío -> 400
    // ---------------------------------------------------------------
    @Test
    void consultaVaciaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Consulta inválida"))
                .andExpect(jsonPath("$.messages[0]").value("La consulta no puede estar vacía"));
    }

    // ---------------------------------------------------------------
    // 3) Sin coincidencias -> 200 con lista vacía y mensaje amigable
    // ---------------------------------------------------------------
    @Test
    void consultaSinCoincidenciasDevuelveListaVacia() throws Exception {
        crearProducto("Notebook Lenovo", "Computadoras", "2500.00", 5, "DISPONIBLE");

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"zzz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos.length()").value(0))
                .andExpect(jsonPath("$.mensaje").value(
                        "No encontré productos para \"zzz\". Prueba con otro nombre o categoría."));
    }

    // ---------------------------------------------------------------
    // 4) Exclusión de no disponibles: RETIRADO, INACTIVO y stock 0 quedan fuera
    // ---------------------------------------------------------------
    @Test
    void excluyeRetiradosInactivosYAgotados() throws Exception {
        crearProducto("Laptop disponible", "Tecnologia", "3000.00", 4, "DISPONIBLE");
        crearProducto("Laptop retirada", "Tecnologia", "2800.00", 2, "RETIRADO");
        crearProducto("Laptop inactiva", "Tecnologia", "2700.00", 2, "INACTIVO");
        crearProducto("Laptop agotada", "Tecnologia", "2600.00", 0, "DISPONIBLE");

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"laptop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos.length()").value(1))
                .andExpect(jsonPath("$.productos[0].nombre").value("Laptop disponible"));
    }

    // ===============================================================
    // Refactor Gemini 2.0 Flash — cliente HTTP simulado con @MockitoBean
    // ===============================================================

    // ---------------------------------------------------------------
    // 5) Gemini responde el JSON estructurado -> se mapea al DTO actual
    // ---------------------------------------------------------------
    @Test
    void geminiDevuelveJsonEstructuradoYSeMapeaAlDto() throws Exception {
        Producto notebook = crearProducto(
                "Notebook Lenovo", "Computadoras", "2500.00", 5, "DISPONIBLE");

        String jsonGemini = "{\"mensaje\":\"Te recomiendo este Notebook\","
                + "\"productosIds\":[" + notebook.getId() + "]}";
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.of(jsonGemini));

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"notebook\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Te recomiendo este Notebook"))
                .andExpect(jsonPath("$.productos.length()").value(1))
                .andExpect(jsonPath("$.productos[0].nombre").value("Notebook Lenovo"))
                .andExpect(jsonPath("$.productos[0].precio").value(2500.00));

        // El System Prompt enviado a Gemini sí lleva el catálogo activo
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generarContenidoJson(prompt.capture(), eq("notebook"));
        assertTrue(prompt.getValue().contains("id=" + notebook.getId()));
        assertTrue(prompt.getValue().contains("Notebook Lenovo"));
        assertTrue(prompt.getValue().contains("productosIds"));
    }

    // ---------------------------------------------------------------
    // 6) Sin respuesta de Gemini (Optional vacío) -> fallback local
    // ---------------------------------------------------------------
    @Test
    void geminiSinRespuestaUsaElRespaldoLocal() throws Exception {
        crearProducto("Notebook Lenovo", "Computadoras", "2500.00", 5, "DISPONIBLE");
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"notebook\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(
                        "Encontré 1 producto para \"notebook\":"))
                .andExpect(jsonPath("$.productos.length()").value(1))
                .andExpect(jsonPath("$.productos[0].nombre").value("Notebook Lenovo"));
    }

    // ---------------------------------------------------------------
    // 7) El cliente lanza excepción (timeout / sin internet) -> fallback, jamás 500
    // ---------------------------------------------------------------
    @Test
    void geminiLanzaExcepcionUsaElRespaldoLocal() throws Exception {
        crearProducto("Mouse", "Accesorios", "50.00", 4, "DISPONIBLE");
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenThrow(new RuntimeException("timeout simulado (sin internet)"));

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"mouse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(
                        "Encontré 1 producto para \"mouse\":"))
                .andExpect(jsonPath("$.productos[0].nombre").value("Mouse"));
    }

    // ---------------------------------------------------------------
    // 8) Gemini devuelve JSON no conforme -> fallback local
    // ---------------------------------------------------------------
    @Test
    void geminiRespondeJsonInvalidoUsaElRespaldoLocal() throws Exception {
        crearProducto("Teclado", "Accesorios", "80.00", 6, "DISPONIBLE");
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.of("esto no es un json valido"));

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"teclado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(
                        "Encontré 1 producto para \"teclado\":"))
                .andExpect(jsonPath("$.productos[0].nombre").value("Teclado"));
    }

    // ---------------------------------------------------------------
    // 9) Regla HU 16 intacta con Gemini: ids no disponibles quedan fuera
    // ---------------------------------------------------------------
    @Test
    void geminiIgnoraIdsNoDisponibles() throws Exception {
        crearProducto("Laptop disponible", "Tecnologia", "3000.00", 4, "DISPONIBLE");
        Producto retirado = crearProducto(
                "Laptop retirada", "Tecnologia", "2800.00", 2, "RETIRADO");

        String jsonGemini = "{\"mensaje\":\"Solo puedo ofrecerte productos activos\","
                + "\"productosIds\":[" + retirado.getId() + "]}";
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.of(jsonGemini));

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"laptop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Solo puedo ofrecerte productos activos"))
                .andExpect(jsonPath("$.productos.length()").value(0));
    }

    // HU 16 - Reconocer un producto dentro de una frase completa.
    @Test
    void consultaConFraseEncuentraArduinoUno() throws Exception {
        crearProducto(
                "Arduino Uno", "Microcontroladores",
                "20.00", 4, "DISPONIBLE"
        );

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"Busco un Arduino Uno\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos.length()").value(1))
                .andExpect(jsonPath("$.productos[0].nombre").value("Arduino Uno"))
                .andExpect(jsonPath("$.productos[0].stock").value(4));
    }

    // HU 16 - Si Gemini no selecciona productos, recuperar
    // coincidencias locales disponibles.
    @Test
    void geminiSinSugerenciasRecuperaArduinoDisponible() throws Exception {
        crearProducto(
                "Arduino Uno", "Microcontroladores",
                "20.00", 4, "DISPONIBLE"
        );

        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.of(
                        "{\"mensaje\":\"No encontré productos\","
                                + "\"productosIds\":[]}"
                ));

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"Busco un Arduino\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos.length()").value(1))
                .andExpect(jsonPath("$.productos[0].nombre").value("Arduino Uno"));
    }

}
