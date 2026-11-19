package com.ejemplo.backenduserapi.controller;

import com.ejemplo.backenduserapi.entity.Usuario;
import com.ejemplo.backenduserapi.service.MercadoPagoOAuthService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MercadoPagoOAuthController {
    private final MercadoPagoOAuthService servicio;
    public MercadoPagoOAuthController(MercadoPagoOAuthService servicio) { this.servicio = servicio; }

    @PostMapping("/api/vendedores/me/mercado-pago/vincular")
    public MercadoPagoOAuthService.Vinculo iniciar(@RequestAttribute("usuarioSesion") Usuario vendedor) {
        return servicio.iniciar(vendedor);
    }

    @GetMapping("/api/vendedores/me/mercado-pago")
    public MercadoPagoOAuthService.Estado estado(@RequestAttribute("usuarioSesion") Usuario vendedor) {
        return servicio.estado(vendedor);
    }

    @GetMapping(value = "/api/mercado-pago/oauth/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(@RequestParam String state, @RequestParam String code) {
        servicio.completar(state, code);
        return "Cuenta de prueba vinculada. Puedes regresar a PulgaTec.";
    }
}
