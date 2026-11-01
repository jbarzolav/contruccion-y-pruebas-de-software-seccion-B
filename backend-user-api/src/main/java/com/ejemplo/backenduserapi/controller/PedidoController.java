package com.ejemplo.backenduserapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.dto.CalificacionRequest;
import com.ejemplo.backenduserapi.dto.ResenaRequest;
import com.ejemplo.backenduserapi.dto.PedidoExperienciaResponse;
import com.ejemplo.backenduserapi.dto.PedidoResponse;
import com.ejemplo.backenduserapi.service.ExperienciaCompraService;
import com.ejemplo.backenduserapi.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final ExperienciaCompraService experienciaCompraService;

    public PedidoController(PedidoService pedidoService, ExperienciaCompraService experienciaCompraService) {
        this.pedidoService = pedidoService;
        this.experienciaCompraService = experienciaCompraService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> confirmarCompra(
            @RequestParam(value = "clienteId", required = false) Long clienteId, HttpServletRequest httpRequest) {
        // HU 23 - El propietario proviene de la sesión.
        clienteId = ((Usuario) httpRequest.getAttribute("usuarioSesion")).getId();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.confirmarCompra(clienteId));
    }

    @GetMapping
    public List<PedidoExperienciaResponse> listarPedidos(HttpServletRequest httpRequest) {
        return experienciaCompraService.listarPedidos((Usuario) httpRequest.getAttribute("usuarioSesion"));
    }

    /** HU 46 - Lista explícita de pedidos propios; la identidad sale del Bearer. */
    @GetMapping("/me")
    public List<PedidoExperienciaResponse> listarMisPedidos(HttpServletRequest httpRequest) {
        return experienciaCompraService.listarPedidos((Usuario) httpRequest.getAttribute("usuarioSesion"));
    }

    @GetMapping("/{idPedido}")
    public PedidoExperienciaResponse obtenerPedido(@PathVariable Long idPedido, HttpServletRequest httpRequest) {
        return experienciaCompraService.obtenerPedido(idPedido,
                (Usuario) httpRequest.getAttribute("usuarioSesion"));
    }

    @PostMapping("/{idPedido}/calificacion")
    public ResponseEntity<PedidoExperienciaResponse> calificar(
            @PathVariable Long idPedido, @Valid @RequestBody CalificacionRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(experienciaCompraService.calificar(
                idPedido, request, (Usuario) httpRequest.getAttribute("usuarioSesion")));
    }

    @PostMapping("/{idPedido}/resena")
    public ResponseEntity<PedidoExperienciaResponse> resenar(
            @PathVariable Long idPedido, @Valid @RequestBody ResenaRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(experienciaCompraService.resenar(
                idPedido, request, (Usuario) httpRequest.getAttribute("usuarioSesion")));
    }
}
