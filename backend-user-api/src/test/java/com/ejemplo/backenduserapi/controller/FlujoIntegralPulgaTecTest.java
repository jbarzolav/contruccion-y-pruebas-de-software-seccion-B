package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.client.GeminiClient;
import com.ejemplo.backenduserapi.repository.CarritoRepository;
import com.ejemplo.backenduserapi.repository.DetallePedidoRepository;
import com.ejemplo.backenduserapi.repository.ItemCarritoRepository;
import com.ejemplo.backenduserapi.repository.PedidoRepository;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auditoría integral del Sprint 1 - recorrido del ciclo de negocio HU 01 → HU 19:
 *
 *  HU 01 registro · HU 02 imagen real · HU 03 edición · HU 04 retiro lógico ·
 *  HU 05 mis productos · HU 06/09 búsqueda y orden · HU 07/08 detalle y catálogo ·
 *  HU 10 disponibilidad · HU 11-14 carrito (agregar, listar, total) ·
 *  HU 15 pedido con descuento de stock · HU 16 chatbot con Gemini simulado ·
 *  HU 17 consultas técnicas · HU 18 beneficios Premium · HU 19 destacados primero.
 *
 * La suite corre 100% offline: el único servicio externo (Gemini) está
 * mockeado con @MockitoBean y la persistencia usa H2 en memoria.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlujoIntegralPulgaTecTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private ItemCarritoRepository itemCarritoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    /** Cliente simulado del recorrido (convención: clienteId = 1). */
    private static final long CLIENTE = 1L;

    /** Vendedor del recorrido (convención: vendedorId = 1, Premium). */
    private static final long VENDEDOR = 1L;

    @MockitoBean
    private GeminiClient geminiClient;

    @BeforeEach
    void preparar() {
        limpiar();
        // Por defecto Gemini no responde -> fallback local de HU 16.
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void finalizar() {
        limpiar();
    }

    private void limpiar() {
        // Orden seguro por claves foráneas.
        detallePedidoRepository.deleteAll();
        itemCarritoRepository.deleteAll();
        carritoRepository.deleteAll();
        pedidoRepository.deleteAll();
        productoRepository.deleteAll();
    }

    @Test
    void recorridoCompletoDelCatalogoAlPedidoYChatbot() throws Exception {

        // ============================================================
        // HU 01 - Registro de publicación
        // ============================================================
        String cuerpoRegistro = mockMvc.perform(post("/api/productos")
                        .contentType("application/json")
                        .content("{\"nombre\":\"Arduino Uno R4\","
                                + "\"descripcion\":\"Placa para prácticas del curso\","
                                + "\"precio\":120.00,\"stock\":10,"
                                + "\"categoria\":\"Microcontroladores\","
                                + "\"estado\":\"DISPONIBLE\","
                                + "\"imagenUrl\":\"/imagenes/arduino.png\","
                                + "\"vendedorId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Arduino Uno R4"))
                .andExpect(jsonPath("$.esDestacado").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode producto = objectMapper.readTree(cuerpoRegistro);
        long idProducto = producto.get("id").asLong();

        // ============================================================
        // HU 02 - Imagen real (multipart)
        // ============================================================
        MockMultipartFile archivo = new MockMultipartFile(
                "file", "arduino.png", "image/png", "png-simulado".getBytes());
        mockMvc.perform(multipart("/api/productos/" + idProducto + "/imagenes")
                        .file(archivo)
                        .param("vendedorId", String.valueOf(VENDEDOR)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imagenUrl").isNotEmpty());

        // ============================================================
        // HU 03 - Edición (nuevo precio) y HU 05 - Mis productos
        // ============================================================
        mockMvc.perform(put("/api/productos/" + idProducto)
                        .param("vendedorId", String.valueOf(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"nombre\":\"Arduino Uno R4\","
                                + "\"descripcion\":\"Placa para prácticas del curso\","
                                + "\"precio\":110.00,\"stock\":10,"
                                + "\"categoria\":\"Microcontroladores\","
                                + "\"estado\":\"DISPONIBLE\","
                                + "\"imagenUrl\":\"/imagenes/arduino.png\","
                                + "\"vendedorId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(110.00));

        mockMvc.perform(get("/api/vendedores/me/productos")
                        .param("vendedorId", String.valueOf(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Arduino Uno R4"));

        // ============================================================
        // HU 07 / HU 08 - Detalle y catálogo
        // ============================================================
        mockMvc.perform(get("/api/productos/" + idProducto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(10));

        mockMvc.perform(get("/api/productos/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // ============================================================
        // HU 06 / HU 09 - Búsqueda con criterio de orden
        // ============================================================
        mockMvc.perform(get("/api/productos")
                        .param("nombre", "Arduino")
                        .param("sort", "precio_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].precio").value(110.00));

        // ============================================================
        // HU 11 - Agregar 2 unidades al carrito
        // ============================================================
        mockMvc.perform(post("/api/carrito/items")
                        .param("clienteId", String.valueOf(CLIENTE))
                        .contentType("application/json")
                        .content("{\"productoId\":" + idProducto + ",\"cantidad\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.subtotal").value(220.00));

        // ============================================================
        // HU 12 / HU 14 - Listado y total del carrito
        // ============================================================
        mockMvc.perform(get("/api/carrito/items")
                        .param("clienteId", String.valueOf(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/carrito/total")
                        .param("clienteId", String.valueOf(CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(220.00));

        // ============================================================
        // HU 15 - Confirmar la compra: 201 y descuento de stock
        // ============================================================
        mockMvc.perform(post("/api/pedidos")
                        .param("clienteId", String.valueOf(CLIENTE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(220.00));

        // ============================================================
        // HU 10 - Disponibilidad tras el pedido (stock 10 - 2 = 8)
        // ============================================================
        mockMvc.perform(get("/api/productos/" + idProducto + "/disponibilidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(8))
                .andExpect(jsonPath("$.disponible").value(true));

        // ============================================================
        // HU 16 - Chatbot de recomendaciones con Gemini simulado
        // ============================================================
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.of(
                        "{\"mensaje\":\"Te recomiendo el Arduino Uno R4\","
                                + "\"productosIds\":[" + idProducto + "]}"));

        mockMvc.perform(post("/api/chatbot/recomendaciones")
                        .contentType("application/json")
                        .content("{\"consulta\":\"arduino\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Te recomiendo el Arduino Uno R4"))
                .andExpect(jsonPath("$.productos[0].nombre").value("Arduino Uno R4"));

        // ============================================================
        // HU 17 - Consulta técnica con respuesta de la IA
        // ============================================================
        when(geminiClient.generarContenidoJson(anyString(), anyString()))
                .thenReturn(Optional.of(
                        "{\"mensaje\":\"El Arduino es una placa de microcontrolador "
                                + "con entradas y salidas digitales.\",\"productosIds\":[]}"));

        mockMvc.perform(post("/api/chatbot/consultas-tecnicas")
                        .contentType("application/json")
                        .content("{\"consulta\":\"¿Qué es un Arduino?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.productos.length()").value(0));

        // ============================================================
        // HU 18 - Beneficios del plan Premium
        // ============================================================
        mockMvc.perform(get("/api/planes/premium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Plan Premium"))
                .andExpect(jsonPath("$.beneficios.length()").value(3));

        // ============================================================
        // HU 19 - Destacar con el vendedor Premium y priorizar en catálogo
        // ============================================================
        mockMvc.perform(patch("/api/productos/" + idProducto + "/destacar")
                        .param("vendedorId", String.valueOf(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esDestacado").value(true))
                .andExpect(jsonPath("$.fechaDestacado").exists());

        mockMvc.perform(get("/api/productos/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].esDestacado").value(true));

        // ============================================================
        // HU 04 - Retiro lógico (baja sin delete) cierra el ciclo
        // ============================================================
        mockMvc.perform(patch("/api/productos/" + idProducto + "/estado")
                        .param("vendedorId", String.valueOf(VENDEDOR))
                        .contentType("application/json")
                        .content("{\"estado\":\"RETIRADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETIRADO"));

        // El retirado desaparece del catálogo pero sigue consultable (baja lógica).
        mockMvc.perform(get("/api/productos/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/productos/" + idProducto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETIRADO"));
    }
}
