package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ImagenProductoRequest;
import com.ejemplo.backenduserapi.dto.ProductoRequest;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/productos")
@CrossOrigin(origins = "*")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @PostMapping
    public ResponseEntity<Producto> crear(
            @Valid @RequestBody ProductoRequest request) {

        Producto creado = productoService.registrar(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(creado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtener(
            @PathVariable Long id) {

        return ResponseEntity
                .ok(productoService.obtenerPorId(id));
    }

    @PostMapping("/{idProducto}/imagenes")
    public ResponseEntity<Producto> agregarImagen(
            @PathVariable Long idProducto,
            @Valid @RequestBody ImagenProductoRequest request) {

        Producto producto = productoService.agregarImagen(
                idProducto,
                request.getImagenUrl(),
                request.getVendedorId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(producto);
    }
}