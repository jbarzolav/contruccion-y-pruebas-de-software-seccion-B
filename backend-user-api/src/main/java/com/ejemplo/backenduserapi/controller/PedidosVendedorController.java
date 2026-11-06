package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.PedidoVendedorResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.PedidosVendedorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;

/** HU 48 - Pedidos asociados al vendedor autenticado. */
@RestController
@RequestMapping("/api/vendedores/me/pedidos")
@CrossOrigin(origins = "*")
public class PedidosVendedorController {
    private final PedidosVendedorService pedidos;

    public PedidosVendedorController(PedidosVendedorService pedidos) {
        this.pedidos = pedidos;
    }

    @GetMapping
    public List<PedidoVendedorResponse> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        Usuario vendedor = (Usuario) request.getAttribute("usuarioSesion");
        if (estado == null && desde == null && hasta == null && productoId == null && page == null && size == null) {
            return pedidos.listarPedidos(vendedor);
        }
        return pedidos.filtrarPedidos(vendedor, estado, desde, hasta, productoId,
                page == null ? 0 : page, size == null ? 100 : size);
    }
}
