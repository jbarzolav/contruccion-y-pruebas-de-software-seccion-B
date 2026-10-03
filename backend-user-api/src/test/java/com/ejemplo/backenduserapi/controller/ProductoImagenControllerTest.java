package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea 6 de la HU 02 - Asociación de imágenes a productos
 * Endpoint: POST /api/productos/{idProducto}/imagenes (multipart/form-data)
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductoImagenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    private Long crearProductoDePrueba(Long vendedorId) {
        Producto producto = new Producto();
        producto.setNombre("Teclado Mecánico");
        producto.setDescripcion("Teclado RGB switches azules");
        producto.setPrecio(new BigDecimal("250.00"));
        producto.setStock(7);
        producto.setCategoria("Accesorios");
        producto.setEstado("DISPONIBLE");
        producto.setImagenUrl("https://via.placeholder.com/300");
        producto.setVendedorId(vendedorId);

        return productoRepository.save(producto).getId();
    }

    private MockMultipartFile archivoValido() {
        return new MockMultipartFile(
                "file",
                "producto.png",
                MediaType.IMAGE_PNG_VALUE,
                "contenido-binario-de-la-imagen".getBytes());
    }

    // 1. Asociación de imagen exitosa -> 201
    @Test
    void asociarImagenExitosamente() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        mockMvc.perform(multipart("/api/productos/{idProducto}/imagenes", idProducto)
                        .file(archivoValido())
                        .param("vendedorId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(idProducto))
                .andExpect(jsonPath("$.imagenUrl").value(org.hamcrest.Matchers.containsString("/imagenes/")));
    }

    // 2. Producto inexistente -> 404
    @Test
    void rechazarCuandoElProductoNoExiste() throws Exception {
        mockMvc.perform(multipart("/api/productos/{idProducto}/imagenes", 999999L)
                        .file(archivoValido())
                        .param("vendedorId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.messages").isNotEmpty());
    }

    // 3. Producto de otro vendedor -> 403
    @Test
    void rechazarCuandoElProductoPerteneceAOtroVendedor() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        mockMvc.perform(multipart("/api/productos/{idProducto}/imagenes", idProducto)
                        .file(archivoValido())
                        .param("vendedorId", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // 4a. Archivo vacío -> 400
    @Test
    void rechazarCuandoElArchivoEstaVacio() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        MockMultipartFile vacio = new MockMultipartFile(
                "file", "vacio.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/productos/{idProducto}/imagenes", idProducto)
                        .file(vacio)
                        .param("vendedorId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // 4b. Formato no permitido -> 400
    @Test
    void rechazarCuandoElFormatoNoEstaPermitido() throws Exception {
        Long idProducto = crearProductoDePrueba(1L);

        MockMultipartFile texto = new MockMultipartFile(
                "file", "notas.txt", MediaType.TEXT_PLAIN_VALUE, "esto no es una imagen".getBytes());

        mockMvc.perform(multipart("/api/productos/{idProducto}/imagenes", idProducto)
                        .file(texto)
                        .param("vendedorId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
