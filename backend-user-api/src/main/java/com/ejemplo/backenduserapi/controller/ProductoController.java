package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.DisponibilidadProductoResponse;
import com.ejemplo.backenduserapi.dto.EstadoProductoRequest;
import com.ejemplo.backenduserapi.dto.ProductoRequest;
import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    // ------------------------------------------------------------------
    // HU 07 - Detalle de producto
    // GET /api/productos/{idProducto} → 200 con la información completa
    // (incluida la imagen asociada). 404 si no existe; si está RETIRADO
    // se responde igual porque es una baja lógica (HU 04).
    // ------------------------------------------------------------------
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

    // ------------------------------------------------------------------
    // HU 04 - Retirar publicación (baja lógica, sin delete físico)
    // PATCH /api/productos/{idProducto}/estado?vendedorId=1
    // Body opcional: { "estado": "RETIRADO" }  (si no llega, se asigna RETIRADO)
    // ------------------------------------------------------------------
    @PatchMapping("/{idProducto}/estado")
    public ResponseEntity<Producto> cambiarEstado(
            @PathVariable Long idProducto,
            @RequestBody(required = false) EstadoProductoRequest request,
            @RequestParam("vendedorId") Long vendedorId) {

        String estado = (request == null) ? null : request.getEstado();

        return ResponseEntity.ok(productoService.cambiarEstado(idProducto, estado, vendedorId));
    }

    // ------------------------------------------------------------------
    // HU 06 / HU 09 - Buscar productos por nombre y ordenar resultados
    // GET /api/productos?nombre={texto}&sort={criterio}
    // ------------------------------------------------------------------
    @GetMapping
    public ResponseEntity<List<Producto>> buscarPorNombre(
            @RequestParam("nombre") String nombre,
            @RequestParam(value = "sort", required = false) String sort) {

        return ResponseEntity.ok(productoService.buscarPorNombre(nombre, sort));
    }
    // ------------------------------------------------------------------
// HU 10 - Consultar disponibilidad de producto
// GET /api/productos/{idProducto}/disponibilidad
// ------------------------------------------------------------------
    @GetMapping("/{idProducto}/disponibilidad")
    public ResponseEntity<DisponibilidadProductoResponse> consultarDisponibilidad(
            @PathVariable Long idProducto) {

        return ResponseEntity.ok(
                productoService.consultarDisponibilidad(idProducto)
        );
    }
    // ------------------------------------------------------------------
    // HU 07 - Catálogo (soporta la HU 08: seleccionar un producto)
    // GET /api/productos/catalogo → solo DISPONIBLE, igual que la búsqueda
    // ------------------------------------------------------------------
    @GetMapping("/catalogo")
    public ResponseEntity<List<Producto>> catalogo() {

        return ResponseEntity.ok(productoService.listarDisponibles());
    }

}