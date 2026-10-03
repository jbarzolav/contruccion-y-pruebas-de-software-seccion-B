package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.ProductoRequest;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/productos")
@CrossOrigin(origins = "*")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    // ------------------------------------------------------------------
    // HU 01 - Registro
    // ------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<Producto> crear(@Valid @RequestBody ProductoRequest request) {

        Producto creado = productoService.registrar(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @GetMapping("/{idProducto}")
    public ResponseEntity<Producto> obtener(@PathVariable Long idProducto) {

        return ResponseEntity.ok(productoService.obtenerPorId(idProducto));
    }

    // ------------------------------------------------------------------
    // HU 02 - Asociar imagen real (multipart)
    // POST /api/productos/{idProducto}/imagenes
    // form-data: file (archivo) + vendedorId (campo)
    // ------------------------------------------------------------------
    @PostMapping(value = "/{idProducto}/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Producto> agregarImagen(
            @PathVariable Long idProducto,
            @RequestPart("file") MultipartFile file,
            @RequestParam("vendedorId") Long vendedorId) {

        Producto producto = productoService.agregarImagen(idProducto, file, vendedorId);

        return ResponseEntity.status(HttpStatus.CREATED).body(producto);
    }

    // ------------------------------------------------------------------
    // HU 03 - Edición
    // PUT /api/productos/{idProducto}?vendedorId=1
    // ------------------------------------------------------------------
    @PutMapping("/{idProducto}")
    public ResponseEntity<Producto> actualizar(
            @PathVariable Long idProducto,
            @Valid @RequestBody ProductoRequest request,
            @RequestParam("vendedorId") Long vendedorId) {

        Producto actualizado = productoService.actualizar(idProducto, request, vendedorId);

        return ResponseEntity.ok(actualizado);
    }
}
