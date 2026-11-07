package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.MetricasVentasResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.MetricasVentasService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * HU 57 - Panel de métricas de ventas del vendedor autenticado.
 *
 * GET /api/vendedores/me/metricas/ventas?desde=&hasta=&compararPeriodoAnterior=
 *
 * La identidad del vendedor sale SIEMPRE de la sesión: el endpoint no acepta
 * ningún identificador de vendedor enviado por el cliente.
 *  - 400 si las fechas son invalidas o el rango es incorrecto.
 *  - 401 si no hay sesion valida.
 *  - 403 si la cuenta no es un vendedor.
 *  - 200 con el panel (incluso sin ventas: los valores llegan en cero).
 */
@RestController
@RequestMapping("/api/vendedores/me/metricas")
@CrossOrigin(origins = "*")
public class MetricasVendedorController {

    private final MetricasVentasService metricas;

    public MetricasVendedorController(MetricasVentasService metricas) {
        this.metricas = metricas;
    }

    @GetMapping("/ventas")
    public MetricasVentasResponse metricasVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false, defaultValue = "true") boolean compararPeriodoAnterior,
            HttpServletRequest request) {
        Usuario vendedor = (Usuario) request.getAttribute("usuarioSesion");
        return metricas.consultar(vendedor, desde, hasta, compararPeriodoAnterior);
    }
}
