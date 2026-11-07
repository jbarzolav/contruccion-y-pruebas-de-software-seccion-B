package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.dto.UmbralStockRequest;
import com.ejemplo.backenduserapi.dto.UmbralStockResponse;
import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.AlertaStockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 58 - Umbral de stock de una publicación propia del vendedor.
 *
 * GET  /api/productos/{idProducto}/umbral-stock → umbral vigente (null si no hay)
 * PUT  /api/productos/{idProducto}/umbral-stock → crea o actualiza el umbral
 *
 * El vendedor se toma de la sesion: el cliente nunca envia su identidad.
 * 401 sin sesion, 403 si el rol no es vendedor o la publicacion es ajena,
 * 404 si la publicacion no existe, 400 si el umbral no es valido.
 */
@RestController
@RequestMapping("/api/productos/{idProducto}/umbral-stock")
@CrossOrigin(origins = "*")
public class UmbralStockController {

    private final AlertaStockService alertas;

    public UmbralStockController(AlertaStockService alertas) {
        this.alertas = alertas;
    }

    @GetMapping
    public UmbralStockResponse consultar(@PathVariable Long idProducto, HttpServletRequest request) {
        return alertas.consultar(vendedor(request), idProducto);
    }

    @PutMapping
    public UmbralStockResponse configurar(@PathVariable Long idProducto,
                                          @Valid @RequestBody UmbralStockRequest cuerpo,
                                          HttpServletRequest request) {
        return alertas.configurar(vendedor(request), idProducto, cuerpo.getUmbral());
    }

    private Usuario vendedor(HttpServletRequest request) {
        return (Usuario) request.getAttribute("usuarioSesion");
    }
}
