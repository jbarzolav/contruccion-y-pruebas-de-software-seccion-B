package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.PedidoVendedorResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.PedidosVendedorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public List<PedidoVendedorResponse> listar(HttpServletRequest request) {
        return pedidos.listarPedidos((Usuario) request.getAttribute("usuarioSesion"));
    }
}
