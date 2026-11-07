package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.AvisoStockBajoResponse;
import com.ejemplo.backenduserapi.dto.UmbralStockResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.AlertaStockService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HU 58 - Panel de umbrales y avisos de stock bajo del vendedor autenticado.
 *
 * GET /api/vendedores/me/umbrales-stock     → publicaciones propias con su umbral
 * GET /api/vendedores/me/avisos-stock-bajo  → avisos STOCK_BAJO del vendedor
 *
 * La identidad sale de la sesion (permiso GESTIONAR_PRODUCTOS): no se acepta
 * ningun identificador de vendedor enviado por el cliente.
 */
@RestController
@RequestMapping("/api/vendedores/me")
@CrossOrigin(origins = "*")
public class UmbralStockVendedorController {

    private final AlertaStockService alertas;

    public UmbralStockVendedorController(AlertaStockService alertas) {
        this.alertas = alertas;
    }

    @GetMapping("/umbrales-stock")
    public List<UmbralStockResponse> umbrales(HttpServletRequest request) {
        return alertas.listar(vendedor(request));
    }

    @GetMapping("/avisos-stock-bajo")
    public List<AvisoStockBajoResponse> avisos(HttpServletRequest request) {
        return alertas.listarAvisos(vendedor(request));
    }

    private Usuario vendedor(HttpServletRequest request) {
        return (Usuario) request.getAttribute("usuarioSesion");
    }
}
