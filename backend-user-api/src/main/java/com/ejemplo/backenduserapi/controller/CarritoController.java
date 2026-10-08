package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.CarritoTotalResponse;
import com.ejemplo.backenduserapi.dto.ItemCarritoCantidadRequest;
import com.ejemplo.backenduserapi.dto.ItemCarritoRequest;
import com.ejemplo.backenduserapi.dto.ItemCarritoResponse;
import com.ejemplo.backenduserapi.service.CarritoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carrito")
@CrossOrigin(origins = "*")
public class CarritoController {

    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    // ------------------------------------------------------------------
    // HU 11 - Agregar un producto al carrito
    // POST /api/carrito/items   Body: { "productoId": 1, "cantidad": 2 }
    // 201 con el ítem (incluye subtotal calculado por el servidor).
    // 400 si la cantidad es < 1 o supera el stock; 404 si el producto no existe.
    // ------------------------------------------------------------------
    @PostMapping("/items")
    public ResponseEntity<ItemCarritoResponse> agregarItem(
            @Valid @RequestBody ItemCarritoRequest request,
            @RequestParam(value = "clienteId", required = false) Long clienteId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(carritoService.agregarItem(clienteId, request));
    }

    // ------------------------------------------------------------------
    // HU 12 - Modificar la cantidad de un ítem
    // PUT /api/carrito/items/{idItemCarrito}   Body: { "cantidad": 3 }
    // 200 con el ítem recalculado (cantidad * precioUnitario).
    // 400 si la cantidad es < 1 o supera el stock; 404 si el ítem no existe.
    // ------------------------------------------------------------------
    @PutMapping("/items/{idItemCarrito}")
    public ResponseEntity<ItemCarritoResponse> actualizarCantidad(
            @PathVariable Long idItemCarrito,
            @Valid @RequestBody ItemCarritoCantidadRequest request) {

        return ResponseEntity.ok(carritoService.actualizarCantidad(idItemCarrito, request));
    }

    // ------------------------------------------------------------------
    // Lectura - listar los ítems del carrito (vista de la HU 12)
    // GET /api/carrito/items -> 200 (lista vacía si no hay carrito)
    // ------------------------------------------------------------------
    // HU 13 - Eliminar un producto del carrito
    // DELETE /api/carrito/items/{idItemCarrito}
    @DeleteMapping("/items/{idItemCarrito}")
    public ResponseEntity<Void> eliminarItem(@PathVariable Long idItemCarrito) {
        carritoService.eliminarItem(idItemCarrito);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/items")
    public ResponseEntity<List<ItemCarritoResponse>> listarItems(
            @RequestParam(value = "clienteId", required = false) Long clienteId) {

        return ResponseEntity.ok(carritoService.listarItems(clienteId));
    }

    // HU 14 - Visualizar el total del carrito
    // GET /api/carrito/total
    @GetMapping("/total")
    public ResponseEntity<CarritoTotalResponse> obtenerTotal(
            @RequestParam(value = "clienteId", required = false) Long clienteId) {

        return ResponseEntity.ok(
                new CarritoTotalResponse(carritoService.calcularTotal(clienteId)));
    }


}
