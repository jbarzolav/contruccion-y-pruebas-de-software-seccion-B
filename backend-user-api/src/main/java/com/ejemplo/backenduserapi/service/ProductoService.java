package com.ejemplo.backenduserapi.service;

import com.ejemplo.backenduserapi.dto.ProductoRequest;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.exception.ImagenInvalidaException;
import com.ejemplo.backenduserapi.exception.ProductoNoEncontradoException;
import com.ejemplo.backenduserapi.exception.PropietarioInvalidoException;
import com.ejemplo.backenduserapi.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Service
public class ProductoService {

    private static final String RUTA_PUBLICA_IMAGENES = "/imagenes/";

    private final ProductoRepository productoRepository;

    /**
     * Carpeta donde se guardan las imágenes reales.
     * Por defecto usa una carpeta temporal del sistema.
     */
    private final Path carpetaImagenes;

    public ProductoService(ProductoRepository productoRepository,
                           @Value("${app.upload.dir:${java.io.tmpdir}/producto-imagenes}") Path carpetaImagenes) {
        this.productoRepository = productoRepository;
        this.carpetaImagenes = carpetaImagenes;
    }

    // ------------------------------------------------------------------
    // HU 01 - Registro de productos
    // ------------------------------------------------------------------
    @Transactional
    public Producto registrar(ProductoRequest request) {
        Producto producto = new Producto();
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecio(request.getPrecio());
        producto.setStock(request.getStock());
        producto.setCategoria(request.getCategoria());
        producto.setEstado(request.getEstado());
        producto.setImagenUrl(request.getImagenUrl());
        producto.setVendedorId(request.getVendedorId());

        return productoRepository.save(producto);
    }

    @Transactional(readOnly = true)
    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(
                        "Producto no encontrado con id " + id));
    }

    // ------------------------------------------------------------------
    // HU 02 - Imágenes (multipart real)
    // ------------------------------------------------------------------
    @Transactional
    public Producto agregarImagen(Long idProducto, MultipartFile file, Long vendedorId) {

        Producto producto = obtenerProductoDelVendedor(idProducto, vendedorId);

        validarImagen(file);

        String nombreGuardado = guardarImagenEnDisco(file);

        producto.setImagenUrl(RUTA_PUBLICA_IMAGENES + nombreGuardado);

        return productoRepository.save(producto);
    }

    /**
     * Valida que el archivo tenga contenido y formato permitido (.jpg, .jpeg, .png).
     */
    private void validarImagen(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ImagenInvalidaException("La imagen no puede estar vacía");
        }

        String nombre = Optional.ofNullable(file.getOriginalFilename())
                .orElse("")
                .toLowerCase(Locale.ROOT);

        boolean extensionPermitida = nombre.endsWith(".jpg")
                || nombre.endsWith(".jpeg")
                || nombre.endsWith(".png");

        String contentType = Optional.ofNullable(file.getContentType()).orElse("");
        boolean tipoPermitido = contentType.isBlank() || contentType.startsWith("image/");

        if (!extensionPermitida || !tipoPermitido) {
            throw new ImagenInvalidaException(
                    "Formato de imagen no permitido. Solo se admiten .jpg, .jpeg y .png");
        }
    }

    private String guardarImagenEnDisco(MultipartFile file) {
        try {
            String original = Optional.ofNullable(file.getOriginalFilename())
                    .orElse("imagen.png")
                    .replaceAll("[^a-zA-Z0-9._-]", "_");

            String nombreGuardado = System.currentTimeMillis() + "_" + original;

            Files.createDirectories(carpetaImagenes);
            file.transferTo(carpetaImagenes.resolve(nombreGuardado));

            return nombreGuardado;

        } catch (IOException e) {
            throw new ImagenInvalidaException("No se pudo guardar la imagen: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // HU 03 - Edición de productos
    // ------------------------------------------------------------------
    @Transactional
    public Producto actualizar(Long idProducto, ProductoRequest request, Long vendedorId) {

        Producto producto = obtenerProductoDelVendedor(idProducto, vendedorId);

        // Campos editables (no se toca id, imagenUrl ni vendedorId)
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecio(request.getPrecio());
        producto.setStock(request.getStock());
        producto.setCategoria(request.getCategoria());
        producto.setEstado(request.getEstado());

        return productoRepository.save(producto);
    }

    // ------------------------------------------------------------------
    // Reglas compartidas
    // ------------------------------------------------------------------

    /**
     * Busca el producto y valida de forma segura (null-safe) que pertenezca al vendedor.
     *
     * @throws ProductoNoEncontradoException -> 404
     * @throws PropietarioInvalidoException  -> 403
     */
    private Producto obtenerProductoDelVendedor(Long idProducto, Long vendedorId) {

        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ProductoNoEncontradoException(
                        "Producto no encontrado con id " + idProducto));

        if (producto.getVendedorId() == null || !producto.getVendedorId().equals(vendedorId)) {
            throw new PropietarioInvalidoException("El producto no pertenece al vendedor");
        }

        return producto;
    }
}
