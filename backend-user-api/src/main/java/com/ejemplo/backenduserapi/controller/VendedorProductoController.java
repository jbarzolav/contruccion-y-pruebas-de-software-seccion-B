package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Producto;
import com.ejemplo.backenduserapi.service.ProductoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendedores")
@CrossOrigin(origins = "*")
public class VendedorProductoController {

    private final ProductoService productoService;

    public VendedorProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    // ------------------------------------------------------------------
    // HU 05 - Mis productos
    // GET /api/vendedores/me/productos?vendedorId=1
    // ------------------------------------------------------------------
    @GetMapping("/me/productos")
    public ResponseEntity<List<Producto>> obtenerMisProductos(
            @RequestParam("vendedorId") Long vendedorId) {

        return ResponseEntity.ok(
                productoService.obtenerProductosDelVendedor(vendedorId)
        );
    }
}