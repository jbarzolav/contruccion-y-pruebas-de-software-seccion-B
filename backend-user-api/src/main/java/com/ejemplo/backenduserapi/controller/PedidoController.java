package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.PedidoResponse;
import com.ejemplo.backenduserapi.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HU 15 - "Confirmar mi compra".
 * POST /api/pedidos -> convierte el carrito activo en un pedido registrado.
 * 201 con el comprobante (idPedido, fecha, total y detalles).
 * 400 si el carrito está vacío o si falta stock (no se descuenta nada).
 */
@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> confirmarCompra(
            @RequestParam(value = "clienteId", required = false) Long clienteId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.confirmarCompra(clienteId));
    }
}
